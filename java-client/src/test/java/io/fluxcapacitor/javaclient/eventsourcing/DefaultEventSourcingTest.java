package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.caching.DefaultCache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultEventSourcingTest {

    private final String modelId = "test";
    private EventStore eventStore = mock(EventStore.class);
    private SnapshotRepository snapshotRepository = mock(SnapshotRepository.class);
    private Cache cache = spy(new DefaultCache());
    private DefaultEventSourcing subject = new DefaultEventSourcing(eventStore, snapshotRepository, cache);

    @Before
    public void setUp() {
        when(eventStore.getDomainEvents(eq(modelId), anyLong())).thenReturn(Stream.empty());
    }

    @Test
    public void testLoadingWithEmptyEventStore() {
        EsModel<TestModel> model = subject.load(modelId, TestModel.class);
        assertNull(model.get());
    }

    @Test
    public void testLoadingFromEventStore() {
        when(eventStore.getDomainEvents(eq(modelId), anyLong()))
                .thenReturn(eventStreamOf(new CreateModel(), new UpdateModel()));
        EsModel<TestModel> model = subject.load(modelId, TestModel.class);
        assertEquals(Arrays.asList(new CreateModel(), new UpdateModel()), model.get().events);
        assertEquals(1L, model.getSequenceNumber());
    }

    @Test
    public void testModelIsLoadedFromCacheWhenPossible() {
        subject.load(modelId, TestModel.class);
        reset(eventStore);
        subject.load(modelId, TestModel.class);
        verifyNoMoreInteractions(eventStore);
    }

    @Test
    public void testModelIsLoadedFromSnapshotWhenPossible() {
        when(snapshotRepository.getSnapshot(modelId))
                .thenReturn(Optional.of(new Aggregate<>(modelId, 0L, new TestModel(new CreateModel()))));
        EsModel<TestModel> model = subject.load(modelId, TestModel.class);
        assertEquals(singletonList(new CreateModel()), model.get().events);
        assertEquals(0L, model.getSequenceNumber());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testApplyEvents() {
        Function<Message, EsModel<TestModel>> f = prepareSubjectForHandling();
        verifyZeroInteractions(eventStore, cache);
        EsModel<TestModel> model = f.apply(new Message(new CreateModel(), EVENT));
        assertEquals(singletonList(new CreateModel()), model.get().events);
        assertEquals(0L, model.getSequenceNumber());
    }

    @Test
    public void testModelIsReadOnlyIfSubjectIsNotIntercepting() {
        EsModel<TestModel> model = subject.load(modelId, TestModel.class);
        assertNull(model.get());
    }

    @Test
    public void testLoadFromRepository() {
        EventSourcingRepository<TestModel> repository = subject.repository(TestModel.class);
        assertNotNull(repository.load(modelId));
    }

    @Test
    public void testLoadFromRepositoryWithSequenceNumber() {
        when(snapshotRepository.getSnapshot(modelId))
                .thenReturn(Optional.of(new Aggregate<>(modelId, 0L, new TestModel(new CreateModel()))));
        EventSourcingRepository<TestModel> repository = subject.repository(TestModel.class);
        assertEquals(singletonList(new CreateModel()), repository.load(modelId).get().events);
    }

    @Test(expected = EventSourcingException.class)
    public void testLoadFromRepoWithUnexpectedSequenceNumber() {
        when(snapshotRepository.getSnapshot(modelId))
                .thenReturn(Optional.of(new Aggregate<>(modelId, 0L, new TestModel(new CreateModel()))));
        EventSourcingRepository<TestModel> repository = subject.repository(TestModel.class);
        repository.load(modelId, 1L);
    }

    @Test
    public void testApplyEventsWithMetadata() {
        EsModel<TestModel> model = prepareSubjectForHandling()
                .apply(new Message(new CreateModelWithMetadata(), Metadata.from("foo", "bar"), EVENT));
        assertEquals(Metadata.from("foo", "bar"), model.get().metadata);
        assertEquals(0L, model.getSequenceNumber());
    }

    @Test
    public void testEventsGetStoredWhenHandlingEnds() {
        reset(eventStore);
        Message event = new Message(new CreateModel(), EVENT);
        prepareSubjectForHandling().apply(event);
        verify(eventStore).storeDomainEvents(modelId, TestModel.class.getSimpleName(), 0L, singletonList(event));
    }

    @Test
    public void testEventsDoNotGetStoredWhenInterceptedMethodTriggersException() {
        Function<DeserializingMessage, Object> f = subject.interceptHandling(s -> {
            EsModel<TestModel> model = subject.load(modelId, TestModel.class);
            reset(cache, eventStore);
            model.apply(new CreateModel());
            throw new IllegalStateException();
        });
        try {
            f.apply(toDeserializingMessage("command"));
            fail();
        } catch (IllegalStateException ignored) {
        }
        verifyZeroInteractions(cache, eventStore);
    }

    @Test
    public void testApplyingUnknownEventsAllowedIfModelExists() {
        reset(eventStore);
        List<Message> events =
                Arrays.asList(new Message(new CreateModel(), EVENT), new Message("foo", EVENT));
        executeWhileIntercepting(() -> {
            EsModel<TestModel> model = subject.load(modelId, TestModel.class);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verify(eventStore).storeDomainEvents(modelId, TestModel.class.getSimpleName(), 1L, events);
    }

    @Test(expected = HandlerNotFoundException.class)
    public void testApplyingUnknownEventsFailsIfModelDoesNotExist() {
        executeWhileIntercepting(
                () -> subject.load(modelId, TestModel.class).apply(new Message("foo", EVENT)))
                .apply(toDeserializingMessage("command"));
    }

    @Test
    public void testSnapshotStoredAfterThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel(), EVENT), new Message("foo", EVENT),
                              new Message("foo", EVENT));
        executeWhileIntercepting(() -> {
            EsModel<TestModelForSnapshotting> model = subject.load(modelId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verify(snapshotRepository).storeSnapshot(new Aggregate<>(modelId, 2L, new TestModelForSnapshotting()));
    }

    @Test
    public void testNoSnapshotStoredBeforeThreshold() {
        List<Message> events =
                Arrays.asList(new Message(new CreateModel(), EVENT), new Message("foo", EVENT));
        executeWhileIntercepting(() -> {
            EsModel<TestModelForSnapshotting> model = subject.load(modelId, TestModelForSnapshotting.class);
            reset(snapshotRepository);
            events.forEach(model::apply);
        }).apply(toDeserializingMessage("command"));
        verifyZeroInteractions(snapshotRepository);
    }

    @SuppressWarnings("unchecked")
    private Function<Message, EsModel<TestModel>> prepareSubjectForHandling() {
        return m -> (EsModel<TestModel>) subject.interceptHandling(s -> subject.load(modelId, TestModel.class).apply(m))
                .apply(toDeserializingMessage(m));
    }

    private Function<DeserializingMessage, Object> executeWhileIntercepting(Runnable task) {
        return subject.interceptHandling(s -> {
            task.run();
            return null;
        });
    }

    private Stream<DeserializingMessage> eventStreamOf(Object... payloads) {
        return stream(payloads).map(this::toDeserializingMessage);
    }

    private DeserializingMessage toDeserializingMessage(Object payload) {
        return toDeserializingMessage(new Message(payload, Metadata.empty(), EVENT));
    }

    private DeserializingMessage toDeserializingMessage(Message message) {
        return new DeserializingMessage(new DeserializingObject<>(
                new SerializedMessage(new Data<>(new byte[0], message.getPayload().getClass().getName(), 0),
                                      message.getMetadata()), message::getPayload), EVENT);
    }

    @EventSourced(cached = true, snapshotPeriod = 100)
    @Value
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
}