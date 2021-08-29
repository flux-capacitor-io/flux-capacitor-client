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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.javaclient.common.ClientUtils.isLocalHandlerMethod;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Slf4j
public class TestFixture implements Given, When {
    /*
        Synchronous test fixture: Messages are dispatched and consumed in the same thread (i.e. all handlers are registered as local handlers)
     */

    public static TestFixture create(Object... handlers) {
        return create(DefaultFluxCapacitor.builder(), handlers);
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return create(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static TestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return create(DefaultFluxCapacitor.builder(), handlersFactory);
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder,
                                     Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(fluxCapacitorBuilder, handlersFactory, InMemoryClient.newInstance(), true);
    }

    /*
        Async test fixture: Messages are dispatched and consumed in different threads (unless a handler is a local handler).
     */

    public static TestFixture createAsync(Object... handlers) {
        return createAsync(DefaultFluxCapacitor.builder(), handlers);
    }

    public static TestFixture createAsync(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return createAsync(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static TestFixture createAsync(Function<FluxCapacitor, List<?>> handlersFactory) {
        return createAsync(DefaultFluxCapacitor.builder(), handlersFactory);
    }

    public static TestFixture createAsync(FluxCapacitorBuilder fluxCapacitorBuilder,
                                          Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(fluxCapacitorBuilder, handlersFactory, InMemoryClient.newInstance(), false);
    }

    public static TestFixture createAsync(FluxCapacitorBuilder fluxCapacitorBuilder, Client client,
                                          Object... handlers) {
        return new TestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers), client, false);
    }

    public static Duration defaultResultTimeout = Duration.ofSeconds(10L);
    public static Duration defaultConsumerTimeout = Duration.ofSeconds(30L);

    @Getter
    private final FluxCapacitor fluxCapacitor;
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    private Duration resultTimeout = defaultResultTimeout;
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    private Duration consumerTimeout = defaultConsumerTimeout;
    private final boolean synchronous;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;

    private final Map<ConsumerConfiguration, List<Message>> consumers = new ConcurrentHashMap<>();
    private final List<Message> commands = new CopyOnWriteArrayList<>(), events = new CopyOnWriteArrayList<>();
    private final List<Schedule> schedules = new CopyOnWriteArrayList<>();
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    private volatile boolean collectingResults;

    protected TestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                          Function<FluxCapacitor, List<?>> handlerFactory, Client client, boolean synchronous) {
        this.synchronous = synchronous;
        Optional<TestUserProvider> userProvider =
                Optional.ofNullable(UserProvider.defaultUserSupplier).map(TestUserProvider::new);
        if (userProvider.isPresent()) {
            fluxCapacitorBuilder = fluxCapacitorBuilder.registerUserSupplier(userProvider.get());
        }
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder.disableShutdownHook().addDispatchInterceptor(interceptor)
                .addBatchInterceptor(interceptor).addHandlerInterceptor(interceptor).build(new TestClient(client));
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        this.registration = registerHandlers(handlerFactory.apply(fluxCapacitor));
    }

    public Registration registerHandlers(Object... handlers) {
        return registerHandlers(Arrays.asList(handlers));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Registration registerHandlers(List<?> handlers) {
        if (handlers.isEmpty()) {
            return Registration.noOp();
        }
        handlers.stream().collect(toMap(Object::getClass, Function.identity(), (a, b) -> {
            log.warn("Handler of type {} is registered more than once. Please make sure this is intentional.",
                     a.getClass());
            return a;
        }));
        if (!synchronous) {
            return getFluxCapacitor().registerHandlers(handlers);
        }
        FluxCapacitor fluxCapacitor = getFluxCapacitor();
        HandlerConfiguration handlerConfiguration = defaultHandlerConfiguration();
        Registration registration = fluxCapacitor.apply(f -> handlers.stream().flatMap(h -> Stream
                        .of(fluxCapacitor.commandGateway().registerHandler(h, handlerConfiguration),
                            fluxCapacitor.queryGateway().registerHandler(h, handlerConfiguration),
                            fluxCapacitor.eventGateway().registerHandler(h, handlerConfiguration),
                            fluxCapacitor.eventStore().registerHandler(h, handlerConfiguration),
                            fluxCapacitor.errorGateway().registerHandler(h, handlerConfiguration)))
                .reduce(Registration::merge).orElse(Registration.noOp()));
        if (fluxCapacitor.scheduler() instanceof DefaultScheduler) {
            DefaultScheduler scheduler = (DefaultScheduler) fluxCapacitor.scheduler();
            registration = registration.merge(fluxCapacitor.apply(fc -> handlers.stream().flatMap(h -> Stream
                            .of(scheduler.registerHandler(h, handlerConfiguration)))
                    .reduce(Registration::merge).orElse(Registration.noOp())));
        } else {
            log.warn("Could not register local schedule handlers");
        }
        return registration;
    }

    /*
        clock & identityProvider
     */

    @Override
    public Given withClock(Clock clock) {
        return getFluxCapacitor().apply(fc -> {
            fc.withClock(clock);
            SchedulingClient schedulingClient = fc.client().getSchedulingClient();
            if (schedulingClient instanceof InMemorySchedulingClient) {
                ((InMemorySchedulingClient) schedulingClient).setClock(clock);
            } else {
                log.warn("Could not update clock of scheduling client. Timing tests may not work.");
            }
            return this;
        });
    }

    public Clock getClock() {
        return getFluxCapacitor().clock();
    }

    @Override
    public IdentityProvider getIdentityProvider() {
        return getFluxCapacitor().identityProvider();
    }

    @Override
    public Given withIdentityProvider(IdentityProvider identityProvider) {
        return getFluxCapacitor().apply(fc -> {
            fc.withIdentityProvider(identityProvider);
            return this;
        });
    }

    /*
        given
     */

    @Override
    public When givenCommands(Object... commands) {
        return given(fc -> getDispatchResult(CompletableFuture.allOf(flatten(commands).map(
                c -> fc.commandGateway().send(c)).toArray(CompletableFuture[]::new))));
    }

    @Override
    public When givenCommandsByUser(User user, Object... commands) {
        return givenCommands(addUser(user, commands));
    }

    @Override
    public When givenDomainEvents(String aggregateId, Object... events) {
        return given(fc -> publishDomainEvents(aggregateId, fc, events));
    }

    @Override
    public When givenEvents(Object... events) {
        return given(fc -> flatten(events).forEach(c -> fc.eventGateway().publish(c)));
    }

    @Override
    public When givenSchedules(Schedule... schedules) {
        return given(fc -> Arrays.stream(schedules).forEach(s -> fc.scheduler().schedule(s)));
    }

    @Override
    public When givenDocument(Object document, String id, String collection, Instant timestamp) {
        return given(fc -> fc.documentStore().index(document, id, collection, timestamp));
    }

    @Override
    public When givenDocuments(String collection, Object... documents) {
        return given(fc -> fc.documentStore().index(Arrays.asList(documents), collection));
    }

    @Override
    public When givenTimeAdvancesTo(Instant instant) {
        return given(fc -> advanceTimeTo(instant));
    }

    @Override
    public When givenTimeElapses(Duration duration) {
        return given(fc -> advanceTimeBy(duration));
    }

    @Override
    public When given(Consumer<FluxCapacitor> condition) {
        return fluxCapacitor.apply(fc -> {
            try {
                condition.accept(fc);
                try {
                    return this;
                } finally {
                    waitForConsumers();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to execute given", e);
            }
        });
    }

    /*
        when
     */

    @Override
    public Then whenCommand(Object command) {
        return whenApplying(fc -> getDispatchResult(fc.commandGateway().send(interceptor.trace(command))));
    }

    @Override
    public Then whenCommandByUser(Object command, User user) {
        return whenCommand(addUser(user, command)[0]);
    }

    @Override
    public Then whenQuery(Object query) {
        return whenApplying(fc -> getDispatchResult(fc.queryGateway().send(interceptor.trace(query))));
    }

    @Override
    public Then whenQueryByUser(Object query, User user) {
        return whenQuery(addUser(user, query)[0]);
    }

    @Override
    public Then whenEvent(Object event) {
        return when(fc -> fc.eventGateway().publish(interceptor.trace(event)));
    }

    @Override
    public Then whenDomainEvents(String aggregateId, Object... events) {
        return when(fc -> publishDomainEvents(aggregateId, fc, events));
    }

    @Override
    public Then whenSearching(String collection, UnaryOperator<Search> searchQuery) {
        return whenApplying(fc -> searchQuery.apply(fc.documentStore().search(collection)).getAll());
    }

    @Override
    public Then whenScheduleExpires(Object schedule) {
        return when(fc -> fc.scheduler().schedule(interceptor.trace(schedule), getClock().instant()));
    }

    @Override
    @SneakyThrows
    public Then whenTimeElapses(Duration duration) {
        return when(fc -> advanceTimeBy(duration));
    }

    @Override
    @SneakyThrows
    public Then whenTimeAdvancesTo(Instant instant) {
        return when(fc -> advanceTimeTo(instant));
    }

    @Override
    public Then when(Consumer<FluxCapacitor> action) {
        return whenApplying(fc -> {
            action.accept(fc);
            return null;
        });
    }

    @Override
    public Then whenApplying(Function<FluxCapacitor, ?> action) {
        return fluxCapacitor.apply(fc -> {
            try {
                handleExpiredSchedulesLocally();
                waitForConsumers();
                resetMocks();
                collectingResults = true;
                Object result;
                try {
                    result = action.apply(fc);
                } catch (Exception e) {
                    result = e;
                }
                waitForConsumers();
                return getResultValidator(result, commands, events, schedules, exceptions);
            } finally {
                handleExpiredSchedulesLocally();
                registration.cancel();
            }
        });
    }

    /*
        helper
     */

    protected Then getResultValidator(Object result, List<Message> commands, List<Message> events,
                                      List<Schedule> schedules, List<Throwable> exceptions) {
        return new ResultValidator(getFluxCapacitor(), result, events, commands, schedules, exceptions);
    }

    protected void publishDomainEvents(String aggregateId, FluxCapacitor fc, Object[] events) {
        List<Message> eventList = flatten(events).map(e -> {
            Message m = e instanceof Message ? (Message) e : new Message(e);
            return m.withMetadata(m.getMetadata().with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, aggregateId));
        }).collect(toList());
        if (eventList.stream().anyMatch(e -> e.getPayload() instanceof Data<?>)) {
            for (int i = 0; i < eventList.size(); i++) {
                Message event = eventList.get(i);
                if (event.getPayload() instanceof Data<?>) {
                    Data<?> eventData = event.getPayload();
                    Data<byte[]> eventBytes = fc.serializer().serialize(eventData);
                    SerializedMessage message =
                            new SerializedMessage(eventBytes, event.getMetadata(), event.getMessageId(),
                                                  event.getTimestamp().toEpochMilli());
                    fc.client().getEventStoreClient().storeEvents(aggregateId, "test", i,
                                                                  singletonList(message), false);
                } else {
                    fc.eventStore().storeEvents(aggregateId, aggregateId, i, event);
                }
            }
        } else {
            fc.eventStore().storeEvents(aggregateId, aggregateId, eventList.size() - 1, eventList);
        }
    }

    protected void handleExpiredSchedulesLocally() {
        if (synchronous) {
            SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
            if (schedulingClient instanceof InMemorySchedulingClient) {
                List<Schedule> expiredSchedules = ((InMemorySchedulingClient) schedulingClient)
                        .removeExpiredSchedules(getFluxCapacitor().serializer());
                if (getFluxCapacitor().scheduler() instanceof DefaultScheduler) {
                    DefaultScheduler scheduler = (DefaultScheduler) getFluxCapacitor().scheduler();
                    expiredSchedules.forEach(s -> scheduler.handleLocally(
                            s, s.serialize(getFluxCapacitor().serializer())));
                }
            }
        }
    }

    protected void waitForConsumers() {
        if (synchronous) {
            return;
        }
        synchronized (consumers) {
            if (!checkConsumers()) {
                try {
                    consumers.wait(consumerTimeout.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!checkConsumers()) {
                log.warn("Some consumers in the test fixture did not finish processing all messages. "
                                 + "This may cause your test to fail. Waiting consumers: {}",
                         consumers.entrySet().stream()
                                 .filter(e -> !e.getValue().isEmpty())
                                 .map(e -> e.getKey().getName() + " : " + e.getValue().stream()
                                 .map(m -> m.getPayload() == null
                                         ? "Void" : m.getPayload().getClass().getSimpleName()).collect(
                                         Collectors.joining(", "))).collect(toList()));
            }
        }
    }

    protected void resetMocks() {
        Client client = fluxCapacitor.client();
        Mockito.reset(Stream.concat(
                Stream.of(client.getEventStoreClient(), client.getSchedulingClient(), client.getKeyValueClient()),
                Arrays.stream(MessageType.values())
                        .flatMap(t -> Stream.of(client.getGatewayClient(t), client.getTrackingClient(t)).filter(
                                Objects::nonNull))).distinct().toArray());
    }

    protected void advanceTimeBy(Duration duration) {
        advanceTimeTo(getClock().instant().plus(duration));
    }

    protected void advanceTimeTo(Instant instant) {
        withClock(Clock.fixed(instant, ZoneId.systemDefault()));
    }

    protected void registerCommand(Message command) {
        commands.add(command);
    }

    protected void registerEvent(Message event) {
        events.add(event);
    }

    protected void registerSchedule(Schedule schedule) {
        schedules.add(schedule);
    }

    protected void registerException(Throwable e) {
        exceptions.add(e);
    }

    @SneakyThrows
    protected Object getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return synchronous
                    ? dispatchResult.get(1, TimeUnit.MILLISECONDS)
                    : dispatchResult.get(resultTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        } catch (TimeoutException e) {
            throw new TimeoutException("Test fixture did not receive a dispatch result in time. "
                                               + "Perhaps some messages did not have handlers?");
        }
    }

    protected Stream<Object> flatten(Object... messages) {
        return Arrays.stream(messages).flatMap(c -> {
            if (c instanceof Collection<?>) {
                return ((Collection<?>) c).stream();
            }
            if (c.getClass().isArray()) {
                return Arrays.stream((Object[]) c);
            }
            return Stream.of(c);
        });
    }

    protected Object[] addUser(User user, Object... messages) {
        UserProvider userProvider = fluxCapacitor.userProvider();
        if (userProvider == null) {
            throw new IllegalStateException("UserProvider has not been configured");
        }
        return flatten(messages).map(o -> o instanceof Message ? (Message) o : new Message(o))
                .map(m -> m.withMetadata(userProvider.addToMetadata(m.getMetadata(), user))).toArray();
    }

    protected boolean checkConsumers() {
        if (synchronous) {
            return true;
        }
        synchronized (consumers) {
            if (consumers.values().stream().allMatch(l -> l.stream().noneMatch(
                    m -> !(m instanceof Schedule) || !((Schedule) m).getDeadline().isAfter(getClock().instant())))) {
                consumers.notifyAll();
                return true;
            }
        }
        return false;
    }

    protected class GivenWhenThenInterceptor implements DispatchInterceptor, BatchInterceptor, HandlerInterceptor {

        private static final String TRACE_TAG = "$givenWhenThen.trace", IGNORE_TAG = "$givenWhenThen.ignore";

        private final Map<MessageType, List<Message>> publishedSchedules = new ConcurrentHashMap<>();

        protected Message trace(Object message) {
            Message result =
                    message instanceof Message ? (Message) message : new Message(message, Metadata.empty());
            return result.withMetadata(result.getMetadata().with(TRACE_TAG, "true"));
        }

        @Override
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                      MessageType messageType) {
            return message -> {
                if (!collectingResults) {
                    message = message.withMetadata(message.getMetadata().with(IGNORE_TAG, "true"));
                }

                DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
                if (currentMessage != null) {
                    if (currentMessage.getMessageType() != SCHEDULE && currentMessage.getMetadata()
                            .containsKey(IGNORE_TAG)) {
                        message = message.withMetadata(message.getMetadata().with(IGNORE_TAG, "true"));
                    }
                    if (currentMessage.getMetadata().containsKey(TRACE_TAG)) {
                        message = message.withMetadata(message.getMetadata().without(TRACE_TAG));
                    }
                }

                if (messageType == SCHEDULE) {
                    addMessage(publishedSchedules.computeIfAbsent(SCHEDULE, t -> new CopyOnWriteArrayList<>()),
                               message);
                }

                synchronized (consumers) {
                    Message interceptedMessage = message;
                    consumers.entrySet().stream()
                            .filter(t -> {
                                ConsumerConfiguration configuration = t.getKey();
                                return (configuration.getMessageType() == messageType && Optional
                                        .ofNullable(configuration.getTypeFilter())
                                        .map(f -> interceptedMessage.getPayload().getClass().getName().matches(f))
                                        .orElse(true));
                            }).forEach(e -> addMessage(e.getValue(), interceptedMessage));
                }
                if (!message.getMetadata().containsAnyKey(IGNORE_TAG, TRACE_TAG)) {
                    switch (messageType) {
                        case COMMAND:
                            registerCommand(message);
                            break;
                        case EVENT:
                            registerEvent(message);
                            break;
                        case SCHEDULE:
                            registerSchedule((Schedule) message);
                            break;
                    }
                }

                return function.apply(message);
            };
        }

        protected void addMessage(List<Message> messages, Message message) {
            if (message instanceof Schedule) {
                messages.removeIf(m -> m instanceof Schedule && ((Schedule) m).getScheduleId()
                        .equals(((Schedule) message).getScheduleId()));
            }
            messages.add(message);
        }

        @Override
        public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
            List<Message> messages = consumers.computeIfAbsent(
                    tracker.getConfiguration(), c -> publishedSchedules.getOrDefault(
                                    c.getMessageType(), emptyList()).stream().filter(m -> Optional.ofNullable(c.getTypeFilter())
                                    .map(f -> m.getPayload().getClass().getName().matches(f)).orElse(true))
                            .collect(toCollection(CopyOnWriteArrayList::new)));
            return b -> {
                consumer.accept(b);
                Collection<String> messageIds =
                        b.getMessages().stream().map(SerializedMessage::getMessageId).collect(toSet());
                messages.removeIf(m -> messageIds.contains(m.getMessageId()));
                checkConsumers();
            };
        }

        @Override
        public Function<DeserializingMessage, Object> interceptHandling(
                Function<DeserializingMessage, Object> function, Handler<DeserializingMessage> handler,
                String consumer) {
            return m -> {
                try {
                    return function.apply(m);
                } catch (Exception e) {
                    registerException(e);
                    throw e;
                } finally {
                    if ((m.getMessageType() == COMMAND || m.getMessageType() == QUERY)
                            && isLocalHandlerMethod(handler.getTarget().getClass(), handler.getMethod(m))) {
                        synchronized (consumers) {
                            consumers.entrySet().stream()
                                    .filter(t -> t.getKey().getMessageType() == m.getMessageType())
                                    .forEach(e -> e.getValue().removeIf(
                                            m2 -> m2.getMessageId()
                                                    .equals(m.getSerializedObject().getMessageId())));
                        }
                        checkConsumers();
                    }
                }
            };
        }

        @Override
        public void shutdown(Tracker tracker) {
            consumers.remove(tracker.getConfiguration());
            checkConsumers();
        }
    }
}
