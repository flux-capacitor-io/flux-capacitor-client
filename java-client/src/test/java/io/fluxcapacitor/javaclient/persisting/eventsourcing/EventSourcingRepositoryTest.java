/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.fluxcapacitor.javaclient.modeling.AssertLegal.HIGHEST_PRIORITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class EventSourcingRepositoryTest {

    private static final String aggregateId = "test";

    static class Normal {
        private final TestFixture testFixture = TestFixture.create(new Handler());
        private final EventStoreClient eventStoreClient = testFixture.getFluxCapacitor().client().getEventStoreClient();

        @Test
        void testLoadingFromEventStore() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .verify(() -> verify(eventStoreClient, times(1)).getEvents(anyString(), anyLong()));
        }

        @Test
        void testLoadingFromEventStoreAfterClearingCache() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .andGiven(() -> testFixture.getFluxCapacitor().cache().invalidateAll())
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .verify(() -> verify(eventStoreClient, times(2)).getEvents(anyString(), anyLong()));
        }

        @Test
        void testModelIsLoadedFromCacheWhenPossible() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .andGiven(() -> testFixture.getFluxCapacitor().queryGateway().sendAndWait(new GetModel()))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(Arrays.asList(new CreateModel(), new UpdateModel()), Metadata.empty()))
                    .verify(() -> verify(eventStoreClient, times(1)).getEvents(anyString(), anyLong()));
        }

        @Test
        void testModelIsReadOnlyIfCurrentMessageIsntCommand() {
            testFixture.givenCommands(new CreateModel()).whenQuery(new ApplyInQuery())
                    .expectException(UnsupportedOperationException.class);
        }

        @Test
        void testApplyEventsWithMetadata() {
            Metadata metaData = Metadata.of("foo", "bar");
            testFixture.givenCommands(new Message(new CreateModelWithMetadata(), metaData)).whenQuery(new GetModel())
                    .expectResult(r -> ((TestModel) r).metadata.entrySet().containsAll(metaData.entrySet()));
        }

        @Test
        void testEventsGetStoredWhenHandlingEnds() {
            testFixture.givenNoPriorActivity().whenCommand(new CreateModel())
                    .verify(() -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId), eq(TestModel.class.getSimpleName()), eq(0L), anyList(), false));
        }

        @Test
        void testEventsDoNotGetStoredWhenHandlerTriggersException() {
            testFixture.givenNoPriorActivity()
                    .whenCommand(new FailToCreateModel())
                    .expectException(MockException.class)
                    .verify(() -> assertEquals(0, eventStoreClient.getEvents(aggregateId, -1L).count()));
        }

        @Test
        void testApplyingUnknownEventsAllowedIfModelExists() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new ApplyNonsense())
                    .expectNoException()
                    .verify(() -> verify(eventStoreClient)
                            .storeEvents(eq(aggregateId), eq(TestModel.class.getSimpleName()), eq(1L), anyList(), false));
        }

        @Test
        void testApplyingUnknownEventsFailsIfModelDoesNotExist() {
            testFixture.givenNoPriorActivity()
                    .whenCommand(new ApplyNonsense())
                    .expectException(HandlerNotFoundException.class)
                    .verify(() -> verify(eventStoreClient, times(0))
                            .storeEvents(anyString(), anyString(), anyLong(), anyList(), false));
        }

        @SuppressWarnings("unchecked")
        @Test
        void testSkippedSequenceNumbers() {
            testFixture.givenCommands(new CreateModel())
                    .andGiven(() -> testFixture.getFluxCapacitor().cache().invalidateAll())
                    .andGiven(() -> when(eventStoreClient.getEvents(anyString(), anyLong())).thenAnswer(invocation -> {
                        AggregateEventStream<SerializedMessage> result =
                                (AggregateEventStream<SerializedMessage>) invocation.callRealMethod();
                        return new AggregateEventStream<>(result.getEventStream(), result.getAggregateId(),
                                                          result.getDomain(), () -> 10L);
                    }))
                    .whenCommand(new UpdateModel())
                    .verify(() -> verify(eventStoreClient).storeEvents(anyString(), anyString(), eq(11L), anyList(),
                                                                       false));
        }


        private static class Handler {
            @HandleCommand
            void handle(Object command, Metadata metadata) {
                FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
            }

            @HandleQuery
            TestModel handle(GetModel query) {
                return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).get();
            }

            @HandleQuery
            TestModel handle(ApplyInQuery query) {
                return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).apply(query).get();
            }

            @HandleCommand
            void handle(ApplyNonsense command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModel.class).apply("nonsense");
            }

        }

        @EventSourced(cached = true, snapshotPeriod = 100)
        @lombok.Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TestModel {
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
            public void handle(UpdateModel event) {
                events.add(event);
            }
        }
    }

    static class Snapshot {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testNoSnapshotStoredBeforeThreshold() {
            testFixture.givenCommands(new CreateModel())
                    .whenCommand(new UpdateModel())
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient(), times(0))
                            .putValue(anyString(), any(), any()));
        }

        @Test
        void testSnapshotStoredAfterThreshold() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getKeyValueClient())
                            .putValue(anyString(), any(), any()));
        }

        @Test
        void testSnapshotRetrieved() {
            testFixture.givenCommands(new CreateModel(), new UpdateModel(), new UpdateModel())
                    .whenCommand(new UpdateModel())
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(),
                                         times(3)).getEvents(aggregateId, -1L))
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(),
                                         times(1)).getEvents(aggregateId, 2L));
        }

        @EventSourced(snapshotPeriod = 3, cached = false)
        @NoArgsConstructor
        @Value
        public static class TestModelForSnapshotting {
            String content = "somecontent";

            @ApplyEvent
            public TestModelForSnapshotting(CreateModel event) {
            }
        }


        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelForSnapshotting.class).assertLegal(command)
                        .apply(command);
            }

        }

    }


    static class WithFactoryMethod {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateUsingFactoryMethod() {
            testFixture.givenNoPriorActivity().whenCommand(new CreateModel())
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyString(), anyLong(), anyList(), false));
        }

        @Test
        void testCreateWithLegalCheckOnNonExistingModelSucceeds() {
            testFixture.givenNoPriorActivity().whenCommand(new CreateModelWithAssertion()).expectNoException();
        }

        @Test
        void testUpdateWithLegalCheckOnNonExistingModelFails() {
            testFixture.givenNoPriorActivity().whenCommand(new UpdateModelWithAssertion())
                    .expectException(MockException.class);
        }

        @Test
        void testAssertionViaInterface() {
            testFixture.givenNoPriorActivity().whenCommand(new CommandWithAssertionInInterface())
                    .expectException(MockException.class);
        }

        @Test
        void testMultipleAssertionMethods() {
            CommandWithMultipleAssertions command = new CommandWithMultipleAssertions();
            testFixture.givenNoPriorActivity().whenCommand(command)
                    .verify(() -> assertEquals(3, command.getAssertionCount().get()));
        }

        @Test
        void testOverriddenAssertion() {
            testFixture.givenNoPriorActivity().whenCommand(new CommandWithOverriddenAssertion()).expectNoException();
        }


        @EventSourced
        public static class TestModelWithFactoryMethod {
            @ApplyEvent
            public static TestModelWithFactoryMethod handle(CreateModel event) {
                return new TestModelWithFactoryMethod();
            }

        }


        private static class Handler {

            @HandleCommand
            void handle(CreateModel command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithFactoryMethod.class).assertLegal(command)
                        .apply(command);
            }

            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithFactoryMethod.class).assertLegal(command);
            }

        }

    }


    static class WithFactoryMethodAndSameInstanceMethod {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateUsingFactoryMethodIfInstanceMethodForSamePayloadExists() {

            testFixture.givenNoPriorActivity().whenCommand(new CreateModel())
                    .verify(() -> verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), times(1))
                            .storeEvents(anyString(), anyString(), anyLong(), anyList(), false));
        }

        @EventSourced
        public static class TestModelWithFactoryMethodAndSameInstanceMethod {
            @ApplyEvent
            public static TestModelWithFactoryMethodAndSameInstanceMethod handleStatic(CreateModel event) {
                return new TestModelWithFactoryMethodAndSameInstanceMethod();
            }

            @ApplyEvent
            public TestModelWithFactoryMethodAndSameInstanceMethod handle(CreateModel event) {
                return this;
            }
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithFactoryMethodAndSameInstanceMethod.class)
                        .assertLegal(command).apply(command);
            }

        }
    }


    static class WithoutFactoryMethodOrConstructor {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testApplyingUnknownEventsFailsIfModelHasNoConstructorOrFactoryMethod() {
            testFixture.givenNoPriorActivity().whenCommand(new CreateModel())
                    .expectException(HandlerNotFoundException.class);
        }

        @EventSourced
        public static class TestModelWithoutFactoryMethodOrConstructor {
            @ApplyEvent
            public TestModelWithoutFactoryMethodOrConstructor handle(CreateModel event) {
                return this;
            }
        }

        private static class Handler {
            @HandleCommand
            void handle(CreateModel command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithoutFactoryMethodOrConstructor.class)
                        .assertLegal(command).apply(command);
            }

        }

    }


    static class WithoutApplyEvent {

        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateViaEvent() {
            testFixture.givenCommands(new CreateModelFromEvent()).whenQuery(new GetModel())
                    .expectResult(r -> ((TestModelWithoutApplyEvent) r).firstEvent.equals(new CreateModelFromEvent()));
        }

        @Test
        void testUpdateViaEvent() {
            testFixture.givenCommands(new CreateModelFromEvent(), new UpdateModelFromEvent()).whenQuery(new GetModel())
                    .expectResult(r -> ((TestModelWithoutApplyEvent) r).secondEvent.equals(new UpdateModelFromEvent()));
        }

        @Test
        void testUpsertViaEventIfNotExists() {
            testFixture.givenCommands(new UpsertModelFromEvent()).whenQuery(new GetModel())
                    .expectResult(r -> ((TestModelWithoutApplyEvent) r).firstEvent.equals(new UpsertModelFromEvent()));
        }

        @Test
        void testUpsertViaEventIfExists() {
            testFixture.givenCommands(new UpsertModelFromEvent(), new UpsertModelFromEvent()).whenQuery(new GetModel())
                    .expectResult(r -> ((TestModelWithoutApplyEvent) r).secondEvent.equals(new UpsertModelFromEvent()));
        }


        @Test
        void testAccessToPrevious() {
            testFixture.givenCommands(new CreateModelFromEvent()).whenCommand(new UpdateModelFromEvent())
                    .verify(() -> {
                        Aggregate<TestModelWithoutApplyEvent> aggregate =
                                testFixture.getFluxCapacitor().aggregateRepository()
                                        .load(aggregateId, TestModelWithoutApplyEvent.class);
                        assertEquals(aggregate.get().firstEvent, aggregate.previous().get().firstEvent);
                        assertEquals(aggregate.get().secondEvent, new UpdateModelFromEvent());
                        assertNull(aggregate.previous().get().secondEvent);
                    });
        }

        @Test
        void testCannotApplyOnPreviousAggregates() {
            testFixture.givenCommands(new CreateModelFromEvent(), new UpdateModelFromEvent())
                    .whenCommand(new ApplyOnPrevious())
                    .expectException(UnsupportedOperationException.class);
        }

        @Test
        void testPlayBackToConditionEndsWithEmptyOptional() {
            testFixture.givenCommands(new CreateModelFromEvent(), new UpsertModelFromEvent())
                    .whenQuery(new GetPlayBackedAggregate()).expectResult(Optional.empty());
        }

        @Value
        public static class CreateModelFromEvent {
            @Apply
            public TestModelWithoutApplyEvent apply() {
                return TestModelWithoutApplyEvent.builder().firstEvent(this).build();
            }
        }

        @Value
        public static class UpsertModelFromEvent {
            @Apply
            public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
                return aggregate == null ? TestModelWithoutApplyEvent.builder().firstEvent(this).build()
                        : aggregate.toBuilder().secondEvent(this).build();
            }
        }

        @Value
        public static class UpdateModelFromEvent {
            @Apply
            public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
                return aggregate.toBuilder().secondEvent(this).build();
            }
        }

        @Value
        public static class ApplyOnPrevious {
            @Apply
            public TestModelWithoutApplyEvent apply(TestModelWithoutApplyEvent aggregate) {
                return aggregate.toBuilder().build();
            }
        }

        @Value
        public static class GetPlayBackedAggregate {
        }

        @EventSourced
        @Value
        @Builder(toBuilder = true)
        public static class TestModelWithoutApplyEvent {
            Object firstEvent, secondEvent;
        }


        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithoutApplyEvent.class).assertLegal(command)
                        .apply(command);
            }

            @HandleQuery
            TestModelWithoutApplyEvent handle(GetModel query) {
                return FluxCapacitor.loadAggregate(aggregateId, TestModelWithoutApplyEvent.class).get();
            }

            @HandleCommand
            void handle(ApplyOnPrevious command) {
                FluxCapacitor.loadAggregate(aggregateId, TestModelWithoutApplyEvent.class).assertLegal(command)
                        .previous().apply(command);
            }

            @HandleQuery
            Optional<Aggregate<TestModelWithoutApplyEvent>> handle(GetPlayBackedAggregate query) {
                return FluxCapacitor.loadAggregate(aggregateId, TestModelWithoutApplyEvent.class)
                        .playBackToCondition(a -> false);
            }
        }
    }

    @Value
    private static class CreateModel {
    }

    @Value
    private static class UpdateModel {
    }

    @Value
    private static class GetModel {
    }

    @Value
    private static class ApplyInQuery {
    }

    @Value
    private static class ApplyNonsense {
    }


    @Value
    private static class FailToCreateModel {
    }


    @Value
    private static class CreateModelWithMetadata {
    }

    @Value
    private static class CreateModelWithAssertion {
        @AssertLegal
        private void assertDoesNotExist(Object model) {
            if (model != null) {
                throw new MockException("Model should not exist");
            }
        }
    }

    @Value
    private static class UpdateModelWithAssertion {
        @AssertLegal
        private void assertExists(Object model) {
            if (model == null) {
                throw new MockException("Model should exist");
            }
        }
    }

    @Value
    private static class CommandWithAssertionInInterface implements ImpossibleAssertion {
    }

    private interface ImpossibleAssertion {
        @AssertLegal
        default void assertTheImpossible(Object model) {
            throw new MockException();
        }
    }

    @Value
    private static class CommandWithMultipleAssertions {
        AtomicInteger assertionCount = new AtomicInteger();

        @AssertLegal
        private void assert1(Object model) {
            assertionCount.addAndGet(1);
        }

        @AssertLegal(priority = HIGHEST_PRIORITY)
        private void assert2(Object model) {
            if (assertionCount.get() > 0) {
                throw new IllegalStateException("Expected to come first");
            }
            assertionCount.addAndGet(2);
        }
    }

    @Value
    private static class CommandWithOverriddenAssertion implements ImpossibleAssertion {
        @Override
        @AssertLegal
        public void assertTheImpossible(Object model) {
            //do nothing
        }
    }
}