package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.caching.DefaultCache;
import io.fluxcapacitor.javaclient.common.model.AssertLegal;
import io.fluxcapacitor.javaclient.common.model.Model;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
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
class DefaultEventSourcingTest {

    private final String modelId = "test";
    private EventStore eventStore = mock(EventStore.class);
    private SnapshotRepository snapshotRepository = mock(SnapshotRepository.class);
    private Cache cache = spy(new DefaultCache());
    private DefaultEventSourcing subject = new DefaultEventSourcing(eventStore, snapshotRepository, cache);

    @BeforeEach
    void setUp() {
        when(eventStore.getDomainEvents(eq(modelId), anyLong())).thenReturn(Stream.empty());
    }

    @Test
    void testLoadingFromEventStore() {
        when(eventStore.getDomainEvents(eq(modelId), anyLong()))
                .thenReturn(eventStreamOf(new CreateModel(), new UpdateModel()));
        Model<TestModel> model = subject.load(modelId, TestModel.class);
        assertEquals(Arrays.asList(new CreateModel(), new UpdateModel()), model.get().events);
        assertEquals(1L, model.getSequenceNumber());
    }

    @Test
    void testModelIsLoadedFromCacheWhenPossible() {
        prepareSubjectForHandling().apply(new Message(new CreateModel()));
        reset(eventStore);
        subject.load(modelId, TestModel.class);
        verifyNoMoreInteractions(eventStore);
    }

    @Test
    void testModelIsLoadedFromSnapshotWhenPossible() {
        when(snapshotRepository.getSnapshot(modelId))
                .thenReturn(Optional.of(new Aggregate<>(modelId, 0L, new TestModel(new CreateModel()))));
        Model<TestModel> model = subject.load(modelId, TestModel.class);
        assertEquals(singletonList(new CreateModel()), model.get().events);
        assertEquals(0L, model.getSequenceNumber());
    }

    @Test
    void testApplyEvents() {
        Function<Message, Model<TestModel>> f = prepareSubjectForHandling();
        verifyNoInteractions(eventStore, cache);
        Model<TestModel> model = f.apply(new Message(new CreateModel()));
        assertEquals(singletonList(new CreateModel()), model.get().events);
        assertEquals(0L, model.getSequenceNumber());
    }

    @Test
    void testModelIsReadOnlyIfSubjectIsNotIntercepting() {
        Model<TestModel> model = subject.load(modelId, TestModel.class);
        assertThrows(EventSourcingException.class, () -> model.apply("whatever"));
    }

    @Test
    void testApplyEventsWithMetadata() {
        Model<TestModel> model = prepareSubjectForHandling()
                .apply(new Message(new CreateModelWithMetadata(), Metadata.from("foo", "bar")));
        assertEquals(Metadata.from("foo", "bar"), model.get().metadata);
        assertEquals(0L, model.getSequenceNumber());
    }

    @Test
    void testEventsGetStoredWhenHandlingEnds() {
        reset(eventStore);
        Message event = new Message(new CreateModel());
        prepareSubjectForHandling().apply(event);
        verify(eventStore).storeDomainEvents(modelId, TestModel.class.getSimpleName(), 0L, singletonList(event));
    }

    @Test
    void testEventsDoNotGetStoredWhenInterceptedMethodTriggersException() {
        Function<DeserializingMessage, Object> f = subject.interceptHandling(s -> {
            Model<TestModel> model = subject.load(modelId, TestModel.class);
            reset(cache, eventStore);
            model.apply(new CreateModel());
            throw new IllegalStateException();
        }, null, "test");
        try {
            f.apply(toDeserializingMessage("command"));
            fail("should not reach this");
        } catch (IllegalStateException ignored) {
        }
        verifyNoInteractions(cache, eventStore);
    }

    @Test
    void testApplyingUnknownEventsAllowedIfModelExists() {
        reset(eventStore);
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"));
        executeWhileIntercepting(() -> {
            Model<TestModel> model = subject.load(modelId, TestModel.class);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verify(eventStore).storeDomainEvents(modelId, TestModel.class.getSimpleName(), 1L, events);
    }

    @Test
    void testApplyingUnknownEventsFailsIfModelDoesNotExist() {
        assertThrows(HandlerNotFoundException.class, () -> executeWhileIntercepting(
                () -> subject.load(modelId, TestModel.class).apply(new Message("foo")))
                .apply(toDeserializingMessage("command")));
    }

    @Test
    void testCreateUsingFactoryMethod() {
        executeWhileIntercepting(() -> subject.load(modelId, TestModelWithFactoryMethod.class)
                .apply(new Message(new CreateModel())))
                .apply(toDeserializingMessage("command"));
    }

    @Test
    void testCreateUsingFactoryMethodIfInstanceMethodForSamePayloadExists() {
        executeWhileIntercepting(() -> subject.load(modelId, TestModelWithFactoryMethodAndSameInstanceMethod.class)
                .apply(new Message(new CreateModel()))
                .apply(new Message(new CreateModel())))
                .apply(toDeserializingMessage("command"));
    }

    @Test
    void testApplyingUnknownEventsFailsIfModelHasNoConstructorOrFactoryMethod() {
        assertThrows(HandlerNotFoundException.class, () -> executeWhileIntercepting(
                () -> subject.load(modelId, TestModelWithoutFactoryMethodOrConstructor.class)
                        .apply(new Message(new CreateModel())))
                .apply(toDeserializingMessage("command")));
    }

    @Test
    void testSnapshotStoredAfterThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"),
                              new Message("foo"));
        executeWhileIntercepting(() -> {
            Model<TestModelForSnapshotting> model = subject.load(modelId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verify(snapshotRepository).storeSnapshot(new Aggregate<>(modelId, 2L, new TestModelForSnapshotting()));
    }

    @Test
    void testNoSnapshotStoredBeforeThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel()), new Message("foo"));
        executeWhileIntercepting(() -> {
            Model<TestModelForSnapshotting> model = subject.load(modelId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void testCreateWithLegalCheckOnNonExistingModelSucceeds() {
        subject.load(modelId, TestModelWithFactoryMethod.class).assertLegal(new CreateModelWithAssertion());
    }

    @Test
    void testUpdateWithLegalCheckOnNonExistingModelFails() {
        assertThrows(MockException.class, () -> subject.load(modelId, TestModelWithFactoryMethod.class)
                .assertLegal(new UpdateModelWithAssertion()));
    }

    @Test
    void testAssertionViaInterface() {
        assertThrows(MockException.class, () -> subject.load(modelId, TestModelWithFactoryMethod.class)
                .assertLegal(new CommandWithAssertionInInterface()));
    }

    @Test
    void testMultipleAssertionMethods() {
        CommandWithMultipleAssertions command = new CommandWithMultipleAssertions();
        subject.load(modelId, TestModelWithFactoryMethod.class).assertLegal(command);
        assertEquals(3, command.getAssertionCount().get());
    }

    @Test
    void testOverriddenAssertion() {
        subject.load(modelId, TestModelWithFactoryMethod.class).assertLegal(new CommandWithOverriddenAssertion());
    }

    @SuppressWarnings("unchecked")
    private Function<Message, Model<TestModel>> prepareSubjectForHandling() {
        return m -> (Model<TestModel>) subject
                .interceptHandling(s -> subject.load(modelId, TestModel.class).apply(m),
                                   null, "test")
                .apply(toDeserializingMessage(m));
    }

    private Function<DeserializingMessage, Object> executeWhileIntercepting(Runnable task) {
        return subject.interceptHandling(s -> {
            task.run();
            return null;
        }, null, "test");
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
                                      message.getTimestamp().toEpochMilli()), message::getPayload), EVENT);
    }

    @EventSourced(cached = true, snapshotPeriod = 100)
    @Value
    @NoArgsConstructor
    public static class TestModel {
        private final List<Object> events = new ArrayList<>();
        private final Metadata metadata = Metadata.empty();

        @ApplyEvent
        public TestModel(CreateModel event) {
            events.add(event);
        }

        @ApplyEvent
        public TestModel(CreateModelWithMetadata event, Metadata metadata) {
            this.metadata.putAll(metadata);
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

        @AssertLegal
        private void assert2(Object model) {
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