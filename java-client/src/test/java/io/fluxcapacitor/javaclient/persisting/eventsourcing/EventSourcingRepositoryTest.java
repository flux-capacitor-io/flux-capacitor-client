/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.Nullable;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    static final String aggregateId = "test";

    @Nested
    class Normal {
        private final TestFixture testFixture =
                TestFixture.create(DefaultFluxCapacitor.builder().disableAutomaticAggregateCaching(), new Handler());
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
                    .given(fc -> fc.cache().invalidateAll())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .expectThat(fc -> verify(eventStoreClient).getEvents(anyString(), anyLong()));
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
            testFixture.givenCommands(new Message(new CreateModelWithMetadata(), metaData)).whenQuery(new GetModel())
                    .<TestModel>expectResult(r -> r.metadata.entrySet().containsAll(metaData.entrySet()));
        }

        @Test
        void testEventsGetStoredWhenHandlingEnds() {
            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId), anyList(),
                                         eq(false)));
        }

        @Test
        void testEventsDoNotGetStoredWhenHandlerTriggersException() {
            testFixture
                    .whenCommand(new FailToCreateModel())
                    .expectExceptionalResult(MockException.class)
                    .expectThat(fc -> assertEquals(0, eventStoreClient.getEvents(aggregateId, -1L).count()));
        }

        @Test
        void testApplyingUnknownEventsAllowedIfModelExists() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new ApplyNonsense())
                    .expectSuccessfulResult()
                    .expectThat(fc -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId), anyList(),
                                         eq(false)));
        }

        @SuppressWarnings("unchecked")
        @Test
        void testSkippedSequenceNumbers() {
            testFixture.givenCommands(new CreateModel())
                    .given(fc -> fc.cache().invalidateAll())
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
                        TestModel testModel = loadAggregate(aggregateId, TestModel.class).get();
                        return testModel.events.equals(List.of(
                                new CreateModel(), new ApplyWhileApplying(), new UpdateModel()));
                    });
        }

        @Test
        void testApplyEventsDuringApplyIsIgnoredDuringReplay() {
            testFixture.givenCommands(new CreateModel(), new ApplyWhileApplying())
                    .given(fc -> fc.cache().invalidateAll())
                    .whenExecuting(fc -> loadAggregate(aggregateId, TestModel.class))
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
                    .givenAppliedEvents(aggregateId, TestModel.class, new UpdateModel(), new UpdateModel())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel(), new UpdateModel()), Metadata.empty()));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command, Metadata metadata) {
                loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
            }

            @HandleCommand
            void handle(FailsAfterApply command, Metadata metadata) {
                loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
                throw new MockException();
            }

            @HandleQuery
            TestModel handle(GetModel query) {
                return loadAggregate(aggregateId, TestModel.class).get();
            }

            @HandleQuery
            TestModel handle(ApplyInQuery query) {
                Entity<TestModel> testModelAggregateRoot = loadAggregate(aggregateId, TestModel.class);
                Entity<TestModel> testModelAggregateRoot1 = testModelAggregateRoot.apply(query);
                return testModelAggregateRoot1.get();
            }

            @HandleCommand
            void handle(ApplyNonsense command) {
                loadAggregate(aggregateId, TestModel.class).apply("nonsense");
            }

        }
    }

    @Aggregate(cached = true, snapshotPeriod = 100)
    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestModel {
        List<Object> events = new ArrayList<>();
        Metadata metadata = Metadata.empty();

        @ApplyEvent
        public TestModel(CreateModel command) {
            events.add(command);
        }


        @ApplyEvent
        public TestModel(FailToCreateModel command) {
            throw new MockException();
        }

        @ApplyEvent
        public TestModel(CreateModelWithMetadata event, Metadata metadata) {
            this.metadata = this.metadata.with(metadata);
            events.add(event);
        }

        @ApplyEvent
        void handle(UpdateModel event) {
            events.add(event);
        }

        @ApplyEvent
        void handle(ApplyWhileApplying event) {
            FluxCapacitor.loadEntity(aggregateId).apply(new UpdateModel());
            events.add(event);
        }
    }

    @Nested
    class SnapshotTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testNoSnapshotStoredBeforeThreshold() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient(), times(0))
                            .putValue(anyString(), any(), any()));
        }

        @Test
        void testSnapshotStoredAfterThreshold() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient())
                            .putValue(anyString(), any(), any()));
        }

        @Test
        void testSnapshotRetrieved() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel(), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(),
                                             times(1)).getEvents(aggregateId, 2L));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId, TestModelForSnapshotting.class).assertLegal(command)
                        .apply(command);
            }

        }
    }

    @Aggregate(snapshotPeriod = 3, cached = false)
    @NoArgsConstructor
    @Value
    static class TestModelForSnapshotting {
        String content = "somecontent";

        @ApplyEvent
        public TestModelForSnapshotting(CreateModel event) {
        }
    }

    @Nested
    class NotEventSourced {
        private final TestFixture testFixture = TestFixture.create(new Handler());

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
                                loadAggregate(aggregateId, TestModelNotEventSourced.class).get();
                        assertTrue(result.getNames().size() == 2
                                           && result.getNames().get(1).equals(UpdateModel.class.getSimpleName()));
                    });
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId, TestModelNotEventSourced.class).assertLegal(command)
                        .apply(command);
            }
        }
    }

    @Aggregate(eventSourced = false, snapshotPeriod = 3, cached = false)
    @Value
    @NoArgsConstructor
    static class TestModelNotEventSourced {
        List<String> names = new ArrayList<>();

        @ApplyEvent
        public TestModelNotEventSourced(CreateModel event) {
            names.add(event.getClass().getSimpleName());
        }

        @ApplyEvent
        public TestModelNotEventSourced apply(UpdateModel event) {
            names.add(event.getClass().getSimpleName());
            return this;
        }
    }

    @Nested
    class WithFactoryMethod {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateUsingFactoryMethod() {
            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyList(), eq(false)));
        }

        private class Handler {
            @HandleCommand
            void handle(CreateModel command) {
                loadAggregate(aggregateId, TestModelWithFactoryMethod.class).apply(command);
            }
        }
    }

    @Aggregate
    static class TestModelWithFactoryMethod {
        @ApplyEvent
        static TestModelWithFactoryMethod handle(CreateModel event) {
            return new TestModelWithFactoryMethod();
        }
    }

    @Nested
    class WithFactoryMethodAndSameInstanceMethod {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateUsingFactoryMethodIfInstanceMethodForSamePayloadExists() {

            testFixture.whenCommand(new CreateModel())
                    .expectThat(fc -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyList(), eq(false)));
        }

        private class Handler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate(aggregateId, TestModelWithFactoryMethodAndSameInstanceMethod.class)
                        .assertLegal(command).apply(command);
            }

        }
    }

    @Aggregate
    static class TestModelWithFactoryMethodAndSameInstanceMethod {
        @ApplyEvent
        static TestModelWithFactoryMethodAndSameInstanceMethod handleStatic(CreateModel event) {
            return new TestModelWithFactoryMethodAndSameInstanceMethod();
        }

        @ApplyEvent
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
                loadAggregate(aggregateId, TestModelWithoutFactoryMethodOrConstructor.class)
                        .assertLegal(command).apply(command);
            }
        }
    }

    @Aggregate
    static class TestModelWithoutFactoryMethodOrConstructor {
        @ApplyEvent
        public TestModelWithoutFactoryMethodOrConstructor handle(CreateModel event) {
            return this;
        }
    }

    @Nested
    class WithoutApplyEvent {

        private TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateViaEvent() {
            testFixture.givenCommands(new CreateModelFromEvent()).whenQuery(new GetModel())
                    .<TestModelWithoutApplyEvent>expectResult(r -> r.firstEvent.equals(new CreateModelFromEvent()));
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
                                        .load(aggregateId, TestModelWithoutApplyEvent.class);
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
                loadAggregate(aggregateId, TestModelWithoutApplyEvent.class).assertLegal(command)
                        .apply(command);
            }

            @HandleQuery
            TestModelWithoutApplyEvent handle(GetModel query) {
                return loadAggregate(aggregateId, TestModelWithoutApplyEvent.class).get();
            }

            @HandleQuery
            Optional<Entity<TestModelWithoutApplyEvent>> handle(GetPlayBackedAggregate query) {
                return loadAggregate(aggregateId, TestModelWithoutApplyEvent.class)
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
}