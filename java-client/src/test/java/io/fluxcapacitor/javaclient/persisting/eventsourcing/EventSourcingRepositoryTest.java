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

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.DefaultCache;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.javaclient.modeling.AssertLegal.HIGHEST_PRIORITY;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Slf4j
class EventSourcingRepositoryTest {

    private final String aggregateId = "test";
    private EventStore eventStore = mock(EventStore.class);
    private SnapshotRepository snapshotRepository = mock(SnapshotRepository.class);
    private Cache cache = spy(new DefaultCache());
    private EventStoreSerializer serializer = spy(new EventStoreSerializer(new JacksonSerializer()));
    private EventSourcingRepository subject = new EventSourcingRepository(eventStore, snapshotRepository, cache, serializer);

    @BeforeEach
    void setUp() {
        when(eventStore.getDomainEvents(eq(aggregateId), anyLong())).thenReturn(Stream.empty());
        when(eventStore.storeDomainEvents(anyString(), anyString(), anyLong(), anyList())).thenReturn(Awaitable.ready());
    }

    @Test
    void testLoadingFromEventStore() {
        when(eventStore.getDomainEvents(eq(aggregateId), anyLong()))
                .thenReturn(eventStreamOf(new CreateModel(), new UpdateModel()));
        Aggregate<TestModel> aggregate = subject.load(aggregateId, TestModel.class);
        assertEquals(Arrays.asList(new CreateModel(), new UpdateModel()), aggregate.get().events);
    }

    @Test
    void testModelIsLoadedFromCacheWhenPossible() {
        applyAndCommit(new Message(new CreateModel()));
        reset(eventStore);
        subject.load(aggregateId, TestModel.class);
        verifyNoMoreInteractions(eventStore);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testModelIsLoadedFromSnapshotWhenPossible() {
        when(snapshotRepository.getSnapshot(aggregateId))
                .thenReturn(Optional.of(new EventSourcedModel(
                        aggregateId, TestModel.class, 0L, null, null, new TestModel(new CreateModel()), null)));
        Aggregate<TestModel> aggregate = subject.load(aggregateId, TestModel.class);
        assertEquals(singletonList(new CreateModel()), aggregate.get().events);
    }

    @Test
    void testApplyEvents() {
        verifyNoInteractions(eventStore, cache);
        Aggregate<TestModel> aggregate = applyAndCommit(new Message(new CreateModel()));
        assertEquals(singletonList(new CreateModel()), aggregate.get().events);
    }

    @Test
    void testModelIsReadOnlyIfCurrentMessageIsntCommand() {
        Aggregate<TestModel> aggregate = subject.load(aggregateId, TestModel.class);
        assertThrows(UnsupportedOperationException.class, () -> aggregate.apply("whatever"));
    }

    @Test
    void testApplyEventsWithMetadata() {
        Metadata metadata = Metadata.of("foo", "bar");
        Aggregate<TestModel> aggregate = applyAndCommit(new Message(new CreateModelWithMetadata(), metadata));
        assertTrue(aggregate.get().metadata.entrySet().containsAll(metadata.entrySet()));
    }

    @Test
    void testEventsGetStoredWhenHandlingEnds() {
        Message event = new Message(new CreateModel());
        applyAndCommit(event);
        verify(eventStore).storeDomainEvents(eq(aggregateId), eq(TestModel.class.getSimpleName()), eq(0L), anyList());
    }

    @Test
    void testEventsDoNotGetStoredWhenHandlerTriggersException() {
        toDeserializingMessage("command").run(m -> {
            Aggregate<TestModel> aggregate = subject.load(aggregateId, TestModel.class);
            reset(cache, eventStore);
            try {
                aggregate.apply(new FailToCreateModel());
                fail("should not reach this");
            } catch (MockException ignored) {
            }
            verifyNoInteractions(cache, eventStore);
        });
    }

    @Test
    void testApplyingUnknownEventsAllowedIfModelExists() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"));
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage("command"))).forEach(m -> {
            Aggregate<TestModel> aggregate = subject.load(aggregateId, TestModel.class);
            reset(eventStore);
            setUp();
            events.forEach(aggregate::apply);
            verifyNoInteractions(eventStore);
        });
        verify(eventStore).storeDomainEvents(eq(aggregateId), eq(TestModel.class.getSimpleName()), eq(1L),
                anyList());
    }

    @Test
    void testApplyingUnknownEventsFailsIfModelDoesNotExist() {
        assertThrows(HandlerNotFoundException.class, () -> toDeserializingMessage("command").run(
                m -> subject.load(aggregateId, TestModel.class).apply(new Message("foo"))));
    }

    @Test
    void testCreateUsingFactoryMethod() {
        toDeserializingMessage("command").run(m -> subject.load(aggregateId, TestModelWithFactoryMethod.class)
                .apply(new Message(new CreateModel())));
    }

    @Test
    void testCreateUsingFactoryMethodIfInstanceMethodForSamePayloadExists() {
        toDeserializingMessage("command")
                .run(m -> subject.load(aggregateId, TestModelWithFactoryMethodAndSameInstanceMethod.class)
                        .apply(new Message(new CreateModel()))
                        .apply(new Message(new CreateModel())));
    }

    @Test
    void testApplyingUnknownEventsFailsIfModelHasNoConstructorOrFactoryMethod() {
        assertThrows(HandlerNotFoundException.class, () -> toDeserializingMessage("command").run(
                m -> subject.load(aggregateId, TestModelWithoutFactoryMethodOrConstructor.class)
                        .apply(new Message(new CreateModel())))
        );
    }

    @Test
    void testSnapshotStoredAfterThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"),
                        new Message("foo"));
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage("command"))).forEach(m -> {
            Aggregate<TestModelForSnapshotting> aggregate = subject.load(aggregateId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(aggregate::apply);
        });
        verify(snapshotRepository).storeSnapshot(argThat(m -> m.sequenceNumber() == 2L));
    }

    @Test
    void testNoSnapshotStoredBeforeThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"));
        toDeserializingMessage("command").run(m -> {
            Aggregate<TestModelForSnapshotting> aggregate = subject.load(aggregateId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(aggregate::apply);
        });
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void testCreateWithLegalCheckOnNonExistingModelSucceeds() {
        subject.load(aggregateId, TestModelWithFactoryMethod.class).assertLegal(new CreateModelWithAssertion());
    }

    @Test
    void testUpdateWithLegalCheckOnNonExistingModelFails() {
        assertThrows(MockException.class, () -> subject.load(aggregateId, TestModelWithFactoryMethod.class)
                .assertLegal(new UpdateModelWithAssertion()));
    }

    @Test
    void testAssertionViaInterface() {
        assertThrows(MockException.class, () -> subject.load(aggregateId, TestModelWithFactoryMethod.class)
                .assertLegal(new CommandWithAssertionInInterface()));
    }

    @Test
    void testMultipleAssertionMethods() {
        CommandWithMultipleAssertions command = new CommandWithMultipleAssertions();
        subject.load(aggregateId, TestModelWithFactoryMethod.class).assertLegal(command);
        assertEquals(3, command.getAssertionCount().get());
    }

    @Test
    void testOverriddenAssertion() {
        subject.load(aggregateId, TestModelWithFactoryMethod.class).assertLegal(new CommandWithOverriddenAssertion());
    }

    @Test
    void testCreateViaEvent() {
        Message message = new Message(new CreateModelFromEvent());
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
        Aggregate<TestModelWithoutApplyEvent> aggregate = subject.load(aggregateId, TestModelWithoutApplyEvent.class);
        assertNotNull(aggregate.get());
        assertEquals(aggregate.get().firstEvent, message.getPayload());
    }

    @Test
    void testUpdateViaEvent() {
        {
            Message message = new Message(new CreateModelFromEvent());
            DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                    .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
        }
        {
            Message message = new Message(new UpdateModelFromEvent());
            DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                    .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
            Aggregate<TestModelWithoutApplyEvent> aggregate = subject.load(aggregateId, TestModelWithoutApplyEvent.class);
            assertNotNull(aggregate.get());
            assertEquals(aggregate.get().secondEvent, message.getPayload());
        }
    }

    @Test
    void testUpsertViaEventIfNotExists() {
        Message message = new Message(new UpsertModelFromEvent());
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
        Aggregate<TestModelWithoutApplyEvent> aggregate = subject.load(aggregateId, TestModelWithoutApplyEvent.class);
        assertNotNull(aggregate.get());
        assertEquals(aggregate.get().firstEvent, message.getPayload());
    }

    @Test
    void testUpsertViaEventIfExists() {
        {
            Message message = new Message(new UpsertModelFromEvent());
            DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                    .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
        }
        {
            Message message = new Message(new UpsertModelFromEvent());
            DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                    .forEach(command -> subject.load(aggregateId, TestModelWithoutApplyEvent.class).apply(message));
            Aggregate<TestModelWithoutApplyEvent> aggregate = subject.load(aggregateId, TestModelWithoutApplyEvent.class);
            assertNotNull(aggregate.get());
            assertEquals(aggregate.get().secondEvent, message.getPayload());
        }
    }

    @Test
    void testAccessToPreviousForImmutableModel() {
        Aggregate<TestModelWithFactoryMethod> oldAggregate = applyAndCommitImmutable(new Message(new CreateModel()));
        Aggregate<TestModelWithFactoryMethod> newAggregate = applyAndCommitImmutable(new Message(new UpdateModel()));
        assertNull(oldAggregate.previous().get());
        assertNull(oldAggregate.previous().previous());
        assertNotEquals(oldAggregate.get(), newAggregate.get());
        assertEquals(oldAggregate.get(), newAggregate.previous().get());
    }

    @Test
    void testAccessToPreviousForMutableModel() {
        Aggregate<TestModel> oldAggregate = applyAndCommit(new Message(new CreateModel()));
        Aggregate<TestModel> newAggregate = applyAndCommit(new Message(new UpdateModel()));
        assertNull(oldAggregate.previous().get());
        assertNull(oldAggregate.previous().previous());
        assertNotEquals(oldAggregate, newAggregate);
        assertEquals(oldAggregate.get(), newAggregate.get());
        assertEquals(oldAggregate.get(), newAggregate.previous().get());
    }

    @Test
    void testCannotApplyOnPreviousAggregates() {
        applyAndCommitImmutable(new Message(new CreateModel()));
        applyAndCommitImmutable(new Message(new UpdateModel()));
        assertThrows(UnsupportedOperationException.class, () -> DeserializingMessage.handleBatch(Stream.of(
                toDeserializingMessage(new Message(new UpdateModel()))))
                .forEach(command -> subject.load(aggregateId, TestModelWithFactoryMethod.class)
                        .previous().apply(command.toMessage())));
    }


    @Test
    void testPlayBackToConditionEndsWithEmptyOptional() {
        applyAndCommitImmutable(new Message(new CreateModel()));
        Aggregate<TestModelWithFactoryMethod> aggregate = applyAndCommitImmutable(new Message(new UpdateModel()));
        assertEquals(Optional.empty(), aggregate.playBackToCondition(a -> false));
    }

    private Aggregate<TestModel> applyAndCommit(Message message) {
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                .forEach(command -> subject.load(aggregateId, TestModel.class).apply(message));
        return subject.load(aggregateId, TestModel.class);
    }

    private Aggregate<TestModelWithFactoryMethod> applyAndCommitImmutable(Message message) {
        DeserializingMessage.handleBatch(Stream.of(toDeserializingMessage(message)))
                .forEach(command -> subject.load(aggregateId, TestModelWithFactoryMethod.class).apply(message));
        return subject.load(aggregateId, TestModelWithFactoryMethod.class);
    }


    private Stream<DeserializingMessage> eventStreamOf(Object... payloads) {
        return stream(payloads).map(this::toDeserializingMessage);
    }

    private DeserializingMessage toDeserializingMessage(Object payload) {
        return toDeserializingMessage(new Message(payload, Metadata.empty()));
    }

    private DeserializingMessage toDeserializingMessage(Message message) {
        return new DeserializingMessage(new DeserializingObject<>(
                new SerializedMessage(new Data<>(new byte[0], message.getPayload().getClass().getName(), 0),
                        message.getMetadata(), message.getMessageId(),
                        message.getTimestamp().toEpochMilli()), message::getPayload), COMMAND);
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

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    public static class TestModelWithoutApplyEvent {
        Object firstEvent, secondEvent;
    }

    @EventSourced(cached = true, snapshotPeriod = 100)
    @lombok.Data
    @NoArgsConstructor
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

    @EventSourced
    public static class TestModelWithFactoryMethod {
        @ApplyEvent
        public static TestModelWithFactoryMethod handle(CreateModel event) {
            return new TestModelWithFactoryMethod();
        }

        @ApplyEvent
        public TestModelWithFactoryMethod handle(UpdateModel event) {
            return new TestModelWithFactoryMethod();
        }
    }

    @EventSourced
    public static class TestModelWithoutFactoryMethodOrConstructor {
        @ApplyEvent
        public TestModelWithoutFactoryMethodOrConstructor handle(CreateModel event) {
            return this;
        }
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

    @EventSourced(snapshotPeriod = 3)
    @NoArgsConstructor
    @Value
    public static class TestModelForSnapshotting {
        @ApplyEvent
        public TestModelForSnapshotting(CreateModel event) {
        }
    }

    @Value
    private static class CreateModel {
    }

    @Value
    private static class FailToCreateModel {
    }

    @Value
    private static class UpdateModel {
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