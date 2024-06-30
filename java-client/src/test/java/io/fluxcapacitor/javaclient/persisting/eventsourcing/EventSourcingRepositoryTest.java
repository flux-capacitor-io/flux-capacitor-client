/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.Nullable;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EventPublicationStrategy;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.modeling.EventPublication.ALWAYS;
import static io.fluxcapacitor.javaclient.modeling.EventPublication.IF_MODIFIED;
import static io.fluxcapacitor.javaclient.modeling.EventPublication.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Slf4j
class EventSourcingRepositoryTest {

    static final TestModelId aggregateId = new TestModelId("test");

    @Nested
    class Normal {
        private final TestFixture testFixture =
                TestFixture.create(DefaultFluxCapacitor.builder().disableAutomaticAggregateCaching(), new Handler())
                        .spy();
        private final EventStoreClient eventStoreClient = testFixture.getFluxCapacitor().client().getEventStoreClient();

        @Test
        void testUpdateBeforeCreateIsAllowedButDoesNothing() {
            testFixture.whenCommand(new UpdateModel()).expectEvents(new UpdateModel()).expectSuccessfulResult();
        }

        @Test
        void testLoadingFromEventStore() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .expectThat(fc -> verifyNoInteractions(eventStoreClient));
        }

        @Test
        void testLoadingFromEventStoreAfterClearingCache() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .given(fc -> fc.cache().clear())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .expectThat(fc -> verify(eventStoreClient).getEvents(eq(aggregateId.toString()), anyLong(), anyInt()));
        }

        @Test
        void testModelIsLoadedFromCacheWhenPossible() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .given(fc -> fc.queryGateway().sendAndWait(new GetModel()))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .expectThat(fc -> verifyNoInteractions(eventStoreClient));
        }

        @Test
        void testApplyEventsWithMetadata() {
            Metadata metaData = Metadata.of("foo", "bar");
            testFixture.givenCommands(new Message(new CreateModelWithMetadata(), metaData))
                    .whenQuery(new GetModel())
                    .<TestModel>expectResult()
                    .expectResult(r -> r.metadata.entrySet().containsAll(metaData.entrySet()));
        }

        @Test
        void testEventsGetStoredWhenHandlingEnds() {
            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId.toString()), anyList(),
                                         eq(false)));
        }

        @Test
        void testEventsDoNotGetStoredWhenHandlerTriggersException() {
            testFixture
                    .whenCommand(new FailToCreateModel())
                    .expectExceptionalResult(MockException.class)
                    .expectThat(fc -> assertEquals(0, eventStoreClient.getEvents(aggregateId.toString(), -1L).count()));
        }

        @Test
        void testApplyingUnknownEventsAllowedIfModelExists() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new ApplyNonsense())
                    .expectSuccessfulResult()
                    .expectThat(fc -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId.toString()), anyList(),
                                         eq(false)));
        }

        @SuppressWarnings("unchecked")
        @Test
        void testSkippedSequenceNumbers() {
            testFixture.givenCommands(new CreateModel())
                    .given(fc -> fc.cache().clear())
                    .whenExecuting(fc -> {
                        when(eventStoreClient.getEvents(anyString(), anyLong())).thenAnswer(invocation -> {
                            AggregateEventStream<SerializedMessage> result =
                                    (AggregateEventStream<SerializedMessage>) invocation.callRealMethod();
                            return new AggregateEventStream<>(result.getEventStream(), result.getAggregateId(),
                                                              () -> 10L);
                        });
                        fc.commandGateway().sendAndForget(new UpdateModel());
                    })
                    .expectThat(fc -> verify(eventStoreClient).storeEvents(anyString(), anyList(),
                                                                           eq(false)));
        }

        @Test
        void testApplyEventsDuringApply() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new ApplyWhileApplying())
                    .expectEvents(new ApplyWhileApplying(), new UpdateModel())
                    .expectTrue(fc -> {
                        TestModel testModel = loadAggregate(aggregateId).get();
                        return testModel.events.equals(List.of(
                                new CreateModel(), new ApplyWhileApplying(), new UpdateModel()));
                    });
        }

        @Test
        void testApplyEventsDuringApplyIsIgnoredDuringReplay() {
            testFixture.givenCommands(new CreateModel(), new ApplyWhileApplying())
                    .given(fc -> fc.cache().clear())
                    .whenExecuting(fc -> loadAggregate(aggregateId))
                    .expectThat(fc -> verify(eventStoreClient, never()).storeEvents(anyString(), anyList(),
                                                                           eq(false)));
        }

        @Test
        void testRollbackAllAppliedEventsAfterException() {
            testFixture.whenCommand(new FailsAfterApply())
                    .expectThat(fc -> verify(eventStoreClient, never()).storeEvents(anyString(), anyList(),
                                                                                    eq(false)))
                    .expectTrue(fc -> fc.eventStore().getEvents(aggregateId).findAny().isEmpty());
        }

        @Test
        void testRollbackAllAppliedEventsAfterException_async() {
            TestFixture.createAsync(new Handler())
                    .whenCommand(new FailsAfterApply())
                    .expectThat(fc -> verify(eventStoreClient, never()).storeEvents(anyString(), anyList(),
                                                                                    eq(false)))
                    .expectTrue(fc -> fc.eventStore().getEvents(aggregateId).findAny().isEmpty());
        }

        @Test
        void applyEventsWithoutLoadingAggregateUpdatesCache() {
            testFixture.givenCommands(new CreateModel())
                    .givenAppliedEvents(aggregateId, TestModel.class, new UpdateModel())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()));
        }

        @Test
        void applyMultipleEventsOutsideHandler() {
            testFixture.givenCommands(new CreateModel())
                    .givenAppliedEvents(aggregateId, new UpdateModel(), new UpdateModel())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel(), new UpdateModel()), Metadata.empty()));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command, Metadata metadata) {
                loadAggregate(aggregateId).assertLegal(command).apply(command, metadata);
            }

            @HandleCommand
            void handle(FailsAfterApply command, Metadata metadata) {
                loadAggregate(aggregateId).assertLegal(command).apply(command, metadata);
                throw new MockException();
            }

            @HandleQuery
            TestModel handle(GetModel query) {
                return loadAggregate(aggregateId).get();
            }

            @HandleQuery
            TestModel handle(ApplyInQuery query) {
                Entity<TestModel> testModelAggregateRoot = loadAggregate(aggregateId);
                Entity<TestModel> testModelAggregateRoot1 = testModelAggregateRoot.apply(query);
                return testModelAggregateRoot1.get();
            }

            @HandleCommand
            void handle(ApplyNonsense command) {
                loadAggregate(aggregateId).apply("nonsense");
            }

        }
    }

    @Nested
    class EventPublicationTests {

        final TestFixture testFixture = TestFixture.create();

        @Test
        void publishIfModified_newAggregate() {
            testFixture.whenCommand(new SelfApplyingCommand(PublishIfModifiedModel.class))
                    .expectEvents(SelfApplyingCommand.class);
        }

        @Test
        void publishIfModified_unmodified() {
            var command = new SelfApplyingCommand(PublishIfModifiedModel.class);
            testFixture.givenCommands(command).whenCommand(command).expectNoEvents();
        }

        @Test
        void publishIfModified_modified() {
            SelfApplyingCommand a = new SelfApplyingCommand(PublishIfModifiedModel.class);
            SelfApplyingCommand b = new SelfApplyingCommand(PublishIfModifiedModel.class);
            assertNotEquals(a, b);
            testFixture.givenCommands(a).whenCommand(b).expectEvents(b);
        }

        @Test
        void publishAlways_newAggregate() {
            testFixture.whenCommand(new SelfApplyingCommand(PublishAlwaysModel.class))
                    .expectEvents(SelfApplyingCommand.class);
        }

        @Test
        void publishAlways_unmodified() {
            var command = new SelfApplyingCommand(PublishAlwaysModel.class);
            testFixture.givenCommands(command).whenCommand(command).expectEvents(SelfApplyingCommand.class);
        }

        @Test
        void publishAlways_modified() {
            SelfApplyingCommand a = new SelfApplyingCommand(PublishIfModifiedModel.class);
            SelfApplyingCommand b = new SelfApplyingCommand(PublishIfModifiedModel.class);
            testFixture.givenCommands(a).whenCommand(b).expectEvents(b);
        }

        @Test
        void publishNever_newAggregate() {
            testFixture.whenCommand(new SelfApplyingCommand(PublishNeverModel.class))
                    .expectNoEvents();
        }

        @Test
        void publishNever_unmodified() {
            var command = new SelfApplyingCommand(PublishNeverModel.class);
            testFixture.givenCommands(command).whenCommand(command).expectNoEvents();
        }

        @Test
        void publishNever_modified() {
            SelfApplyingCommand a = new SelfApplyingCommand(PublishNeverModel.class);
            SelfApplyingCommand b = new SelfApplyingCommand(PublishNeverModel.class);
            testFixture.givenCommands(a).whenCommand(b).expectNoEvents();
        }

        @Test
        void publishNeverToAlways_newAggregate() {
            var a = new SelfApplyingCommand(PublishNeverOverwrittenModel.class);
            testFixture.whenCommand(a).expectEvents(a);
        }
    }

    @Nested
    class DepthTests {
        final TestFixture testFixture = TestFixture.create().spy();

        @Test
        void reloadLazilyAfterDepth() {
            SelfApplyingCommand command = new SelfApplyingCommand(ZeroDepthModel.class);
            testFixture.givenCommands(command, command, command)
                    .whenApplying(fc -> {
                        Entity<Object> test = FluxCapacitor.loadEntity("test");
                        return test.previous().get();
                    }).expectNonNullResult()
                    .expectThat(fc -> verify(fc.client().getEventStoreClient()).getEvents("test", 0L, 1));
        }

        @Test
        void dontReloadOnCheckpoint() {
            SelfApplyingCommand command = new SelfApplyingCommand(CheckpointModel.class);
            testFixture.givenCommands(command, command, command, command, command)
                    .whenApplying(fc -> {
                        Entity<Object> test = FluxCapacitor.loadEntity("test");
                        return test.previous().get();
                    }).expectNonNullResult()
                    .expectThat(fc -> verify(fc.client().getEventStoreClient(), never()).getEvents(eq("test"), anyLong(), anyInt()));
        }

        @Test
        void reloadBeforeCheckpoint() {
            SelfApplyingCommand command = new SelfApplyingCommand(CheckpointModel.class);
            testFixture.givenCommands(command, command, command, command, command)
                    .whenApplying(fc -> {
                        Entity<Object> test = FluxCapacitor.loadEntity("test");
                        return test.previous().previous().get();
                    }).expectNonNullResult()
                    .expectThat(fc -> verify(fc.client().getEventStoreClient()).getEvents("test", 0L, 2));
        }
    }

    @Aggregate(eventPublication = IF_MODIFIED)
    @Value
    static class PublishIfModifiedModel {

        Object event;

        @Apply
        PublishIfModifiedModel(Object event) {
            this.event = event;
        }

        @Apply
        PublishIfModifiedModel apply(Object event) {
            return new PublishIfModifiedModel(event);
        }
    }

    @Aggregate(eventPublication = ALWAYS)
    @Value
    static class PublishAlwaysModel {
        Object event;

        @Apply
        PublishAlwaysModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(eventPublication = NEVER)
    @Value
    static class PublishNeverModel {
        Object event;

        @Apply
        PublishNeverModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(eventPublication = NEVER)
    @Value
    static class PublishNeverOverwrittenModel {
        Object event;

        @Apply(eventPublication = IF_MODIFIED)
        PublishNeverOverwrittenModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(cachingDepth = 0)
    @Value
    static class ZeroDepthModel {
        Object event;

        @Apply
        ZeroDepthModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(cachingDepth = 0, checkpointPeriod = 3)
    @Value
    static class CheckpointModel {
        Object event;

        @Apply
        CheckpointModel(Object event) {
            this.event = event;
        }
    }

    @Nested
    class PublicationStrategyTests {

        final TestFixture testFixture = TestFixture.create().spy();

        @Test
        void storeOnly() {
            testFixture
                    .registerHandlers(new Object() {
                        @HandleEvent
                        void handle(Object event) {
                            throw new MockException();
                        }
                    })
                    .whenCommand(new SelfApplyingCommand(StoreOnlyModel.class)).expectNoErrors()
                    .expectThat(fc -> verify(fc.client().getEventStoreClient()).storeEvents(any(), anyList(), eq(true)));
        }

        @Test
        void publishOnly() {
            testFixture
                    .registerHandlers(new Object() {
                        @HandleEvent
                        void handle(Object event) {
                            FluxCapacitor.publishMetrics("success");
                        }
                    })
                    .whenCommand(new SelfApplyingCommand(PublishOnlyModel.class))
                    .expectMetrics("success")
                    .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.EVENT)).append(any(), any()))
                    .expectThat(fc -> verify(fc.client().getEventStoreClient(), never()).storeEvents(any(), anyList(), anyBoolean()));
        }

    }

    @Aggregate(publicationStrategy = EventPublicationStrategy.STORE_ONLY)
    @Value
    static class StoreOnlyModel {
        Object event;
        @Apply
        StoreOnlyModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(publicationStrategy = EventPublicationStrategy.PUBLISH_ONLY)
    @Value
    static class PublishOnlyModel {
        Object event;
        @Apply
        PublishOnlyModel(Object event) {
            this.event = event;
        }
    }

    @Aggregate(cached = true)
    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestModel {
        List<Object> events = new ArrayList<>();
        Metadata metadata = Metadata.empty();

        @Apply
        public TestModel(CreateModel command) {
            events.add(command);
        }


        @Apply
        public TestModel(FailToCreateModel command) {
            throw new MockException();
        }

        @Apply
        public TestModel(CreateModelWithMetadata event, Metadata metadata) {
            this.metadata = this.metadata.with(metadata);
            events.add(event);
        }

        @Apply
        void handle(UpdateModel event) {
            events.add(event);
        }

        @Apply
        void handle(ApplyWhileApplying event) {
            FluxCapacitor.loadEntity(aggregateId).apply(new UpdateModel());
            events.add(event);
        }
    }

    static class TestModelId extends Id<TestModel> {
        public TestModelId(String functionalId) {
            super(functionalId, "testId-", true);
        }
    }

    @Nested
    class SnapshotTests {
        SnapshotTestModelId aggregateId = new SnapshotTestModelId("test");

        private final TestFixture testFixture = TestFixture.create(new Handler()).spy();

        @Test
        void testNoSnapshotStoredBeforeThreshold() {
            testFixture.givenCommands(new CreateSnapshotModel(aggregateId))
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient(), times(0))
                            .putValue(anyString(), any(), any()))
            ;
        }

        @Test
        void testSnapshotStoredAfterThreshold() {
            testFixture.givenCommands(new CreateSnapshotModel(aggregateId), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient())
                            .putValue(anyString(), any(), any()));
        }

        @Test
        void testSnapshotRetrieved() {
            testFixture.givenCommands(new CreateSnapshotModel(aggregateId), new UpdateModel(), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(),
                                             times(1)).getEvents(aggregateId.toString(), 2L, -1));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId).assertLegal(command).apply(command);
            }

        }
    }

    static class SnapshotTestModelId extends Id<TestModelForSnapshotting> {
        SnapshotTestModelId(String functionalId) {
            super(functionalId, TestModelForSnapshotting.class, "testId-", true);
        }
    }

    record CreateSnapshotModel(SnapshotTestModelId id) {
    }

    @Aggregate(snapshotPeriod = 3, cached = false)
    @Value
    static class TestModelForSnapshotting {
        SnapshotTestModelId id;
        String content = "somecontent";

        @Apply
        public static TestModelForSnapshotting create(CreateSnapshotModel event) {
            return new TestModelForSnapshotting(event.id());
        }

        @Apply
        TestModelForSnapshotting update(UpdateModel event) {
            return this;
        }
    }

    @Nested
    class NotEventSourced {
        private final TestFixture testFixture = TestFixture.create(new Handler()).spy();

        @Test
        void testIgnoreSnapshotPeriodWhenNotEventSourced() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> {
                        verify(testFixture.getFluxCapacitor().client().getEventStoreClient(),
                               times(0)).getEvents(anyString(), anyLong());
                        verify(testFixture.getFluxCapacitor().client().getKeyValueClient(), times(1))
                                .putValue(anyString(), any(), any());
                        TestModelNotEventSourced result =
                                loadAggregate(aggregateId.toString(), TestModelNotEventSourced.class).get();
                        assertTrue(result.getNames().size() == 2
                                           && result.getNames().get(1).equals(UpdateModel.class.getSimpleName()));
                    });
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId.toString(), TestModelNotEventSourced.class).assertLegal(command)
                        .apply(command);
            }
        }
    }

    @Aggregate(eventSourced = false, snapshotPeriod = 3, cached = false)
    @Value
    @NoArgsConstructor
    static class TestModelNotEventSourced {
        List<String> names = new ArrayList<>();

        @Apply
        public TestModelNotEventSourced(CreateModel event) {
            names.add(event.getClass().getSimpleName());
        }

        @Apply
        public TestModelNotEventSourced apply(UpdateModel event) {
            names.add(event.getClass().getSimpleName());
            return this;
        }
    }

    @Nested
    class WithFactoryMethod {
        private final TestFixture testFixture = TestFixture.create(new Handler()).spy();

        @Test
        void testCreateUsingFactoryMethod() {
            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyList(), eq(false)));
        }

        private class Handler {
            @HandleCommand
            void handle(CreateModel command) {
                loadAggregate(aggregateId.toString(), TestModelWithFactoryMethod.class).apply(command);
            }
        }
    }

    @Aggregate
    static class TestModelWithFactoryMethod {
        @Apply
        static TestModelWithFactoryMethod handle(CreateModel event) {
            return new TestModelWithFactoryMethod();
        }
    }

    @Nested
    class WithFactoryMethodAndSameInstanceMethod {

        private final TestFixture testFixture = TestFixture.create(new Handler()).spy();

        @Test
        void testCreateUsingFactoryMethodIfInstanceMethodForSamePayloadExists() {

            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyList(), eq(false)));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId.toString(), TestModelWithFactoryMethodAndSameInstanceMethod.class)
                        .assertLegal(command).apply(command);
            }

        }
    }

    @Aggregate
    static class TestModelWithFactoryMethodAndSameInstanceMethod {
        @Apply
        static TestModelWithFactoryMethodAndSameInstanceMethod handleStatic(CreateModel event) {
            return new TestModelWithFactoryMethodAndSameInstanceMethod();
        }

        @Apply
        public TestModelWithFactoryMethodAndSameInstanceMethod handle(CreateModel event) {
            return this;
        }
    }

    @Nested
    class WithoutFactoryMethodOrConstructor {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        private class Handler {
            @HandleCommand
            void handle(CreateModel command) {
                loadAggregate(aggregateId.toString(), TestModelWithoutFactoryMethodOrConstructor.class)
                        .assertLegal(command).apply(command);
            }
        }
    }

    @Aggregate
    static class TestModelWithoutFactoryMethodOrConstructor {
        @Apply
        public TestModelWithoutFactoryMethodOrConstructor handle(CreateModel event) {
            return this;
        }
    }

    @Nested
    class WithoutApplyEvent {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateViaEvent() {
            testFixture.givenCommands(new CreateModelFromEvent())
                    .whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new CreateModelFromEvent()));
        }

        @Test
        void testCreateViaEventInterfaceMethod() {
            testFixture.givenCommands(new CreateModelFromEventConcrete()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new CreateModelFromEventConcrete()));
        }

        @Test
        void testUpdateViaEvent() {
            testFixture.givenCommands(new CreateModelFromEvent(), new UpdateModelFromEvent()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.secondEvent.equals(new UpdateModelFromEvent()));
        }

        @Test
        void testUpsertViaEventIfNotExists() {
            testFixture.givenCommands(new UpsertModelFromEvent()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new UpsertModelFromEvent()));
        }

        @Test
        void testUpsertViaEventIfExists() {
            testFixture.givenCommands(new UpsertModelFromEvent(), new UpsertModelFromEvent()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.secondEvent.equals(new UpsertModelFromEvent()));
        }

        @Test
        void testUpsertViaEventIfNotExists_nullableModel() {
            testFixture.givenCommands(new UpsertModelWithNullable()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new UpsertModelWithNullable()));
        }

        @Test
        void testUpsertViaEventIfNotExists_nullableModelInterface() {
            testFixture.givenCommands(new UpsertModelWithNullableClass()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new UpsertModelWithNullableClass()));
        }

        @Test
        void testUpsertViaEventIfExists_nullableModel() {
            testFixture.givenCommands(new UpsertModelWithNullable(), new UpsertModelWithNullable())
                    .whenQuery(new GetModel()).<TestModelWithoutApplyEvent>expectResult(r -> r.secondEvent.equals(new UpsertModelWithNullable()));
        }

        @Test
        void testAccessToPrevious() {
            testFixture.givenCommands(new CreateModelFromEvent()).whenCommand(new UpdateModelFromEvent())
                    .expectThat(fc -> {
                        Entity<TestModelWithoutApplyEvent> aggregateRoot =
                                testFixture.getFluxCapacitor().aggregateRepository()
                                        .load(aggregateId.toString(), TestModelWithoutApplyEvent.class);
                        assertEquals(aggregateRoot.get().firstEvent, aggregateRoot.previous().get().firstEvent);
                        assertEquals(aggregateRoot.get().secondEvent, new UpdateModelFromEvent());
                        assertNull(aggregateRoot.previous().get().secondEvent);
                    });
        }

        @Test
        void testPlayBackToConditionEndsWithEmptyOptional() {
            testFixture.givenCommands(new CreateModelFromEvent(), new UpsertModelFromEvent())
                    .whenQuery(new GetPlayBackedAggregate()).expectResult(Optional.empty());
        }

        @Test
        void testApplyInApply() {
            @Value
            class CreateAnotherAggregateInApply {
                String nextAggregate;

                @Apply
                TestModelWithoutApplyEvent apply() {
                    loadAggregate(nextAggregate, TestModelWithoutApplyEvent.class).apply(new CreateModelFromEvent());
                    return TestModelWithoutApplyEvent.builder().firstEvent(this).build();
                }
            }

            testFixture
                    .whenCommand(new CreateAnotherAggregateInApply("another"))
                    .expectOnlyEvents(new CreateAnotherAggregateInApply("another"),
                                      new CreateModelFromEvent());
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId.toString(), TestModelWithoutApplyEvent.class).assertLegal(command)
                        .apply(command);
            }

            @HandleQuery
            TestModelWithoutApplyEvent handle(GetModel query) {
                return loadAggregate(aggregateId.toString(), TestModelWithoutApplyEvent.class).get();
            }

            @HandleQuery
            Optional<Entity<TestModelWithoutApplyEvent>> handle(GetPlayBackedAggregate query) {
                return loadAggregate(aggregateId.toString(), TestModelWithoutApplyEvent.class)
                        .playBackToCondition(a -> false);
            }
        }
    }

    @Value
    static class CreateModelFromEvent {
        @Apply
        public TestModelWithoutApplyEvent apply() {
            return TestModelWithoutApplyEvent.builder().firstEvent(this).build();
        }
    }

    interface CreateModelFromEventInterface {
        @Apply
        TestModelWithoutApplyEvent apply();
    }

    @Value
    static class CreateModelFromEventConcrete implements CreateModelFromEventInterface {
        @Override
        public TestModelWithoutApplyEvent apply() {
            return TestModelWithoutApplyEvent.builder().firstEvent(this).build();
        }
    }

    @Value
    static class UpsertModelFromEvent {
        @Apply
        public TestModelWithoutApplyEvent apply() {
            return TestModelWithoutApplyEvent.builder().firstEvent(this).build();
        }

        @Apply
        public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
            return aggregate.toBuilder().secondEvent(this).build();
        }
    }

    @Value
    static class UpsertModelWithNullable {
        @Apply
        public TestModelWithoutApplyEvent apply(@Nullable TestModelWithoutApplyEvent aggregate) {
            return aggregate == null
                    ? TestModelWithoutApplyEvent.builder().firstEvent(this).build()
                    : aggregate.toBuilder().secondEvent(this).build();
        }
    }

    @Value
    static class UpsertModelWithNullableClass implements UpsertModelWithNullableInterface {
        @Override
        public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
            return aggregate == null
                    ? TestModelWithoutApplyEvent.builder().firstEvent(this).build()
                    : aggregate.toBuilder().secondEvent(this).build();
        }
    }

    interface UpsertModelWithNullableInterface {
        @Apply TestModelWithoutApplyEvent apply(@Nullable TestModelWithoutApplyEvent aggregate);
    }

    @Value
    static class UpdateModelFromEvent {
        @Apply
        public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
            return aggregate.toBuilder().secondEvent(this).build();
        }
    }

    @Value
    static class GetPlayBackedAggregate {
    }

    @Aggregate
    @Value
    @Builder(toBuilder = true)
    static class TestModelWithoutApplyEvent {
        Object firstEvent, secondEvent;
    }

    @Value
    static class CreateModel {
    }

    @Value
    static class UpdateModel {
    }

    @Value
    static class ApplyWhileApplying {
    }

    @Value
    static class GetModel {
    }

    @Value
    static class ApplyInQuery {
    }

    @Value
    static class ApplyNonsense {
    }


    @Value
    static class FailToCreateModel {
    }

    @Value
    static class FailsAfterApply {
    }


    @Value
    static class CreateModelWithMetadata {
    }

    @AllArgsConstructor
    static class SelfApplyingCommand {
        Class<?> aggregateClass;

        @HandleCommand
        void handle() {
            loadAggregate("test", aggregateClass).assertAndApply(this);
        }
    }
}