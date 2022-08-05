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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.serialization.JsonUtils;
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
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Executable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.common.ClientUtils.isLocalHandler;
import static io.fluxcapacitor.javaclient.common.ClientUtils.runSilently;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Slf4j
public class TestFixture implements Given, When {
    private static final String TRACE_TAG = "$givenWhenThen.trace", IGNORE_TAG = "$givenWhenThen.ignore";
    
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
    private final List<Message> commands = new CopyOnWriteArrayList<>(), events = new CopyOnWriteArrayList<>(),
            webRequests = new CopyOnWriteArrayList<>();
    private final List<Schedule> schedules = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

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
        this.fluxCapacitor = new TestFluxCapacitor(
                fluxCapacitorBuilder.disableShutdownHook().addDispatchInterceptor(interceptor)
                        .addBatchInterceptor(interceptor).addHandlerInterceptor(interceptor, true)
                        .build(new TestClient(client)));
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        withIdentityProvider(new PredictableIdFactory());
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
        BiPredicate<Class<?>, Executable> handlerFilter = (c, e) -> true;
        Registration registration = fluxCapacitor.apply(f -> handlers.stream().flatMap(h -> Stream
                        .of(fluxCapacitor.commandGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.queryGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.eventGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.eventStore().registerHandler(h, handlerFilter),
                            fluxCapacitor.errorGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.webRequestGateway().registerHandler(h, handlerFilter)))
                .reduce(Registration::merge).orElse(Registration.noOp()));
        if (fluxCapacitor.scheduler() instanceof DefaultScheduler) {
            DefaultScheduler scheduler = (DefaultScheduler) fluxCapacitor.scheduler();
            registration = registration.merge(fluxCapacitor.apply(fc -> handlers.stream().flatMap(h -> Stream
                            .of(scheduler.registerHandler(h, handlerFilter)))
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
    public TestFixture withClock(Clock clock) {
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
    public TestFixture withIdentityProvider(IdentityProvider identityProvider) {
        return getFluxCapacitor().apply(fc -> {
            fc.withIdentityProvider(identityProvider);
            return this;
        });
    }

    /*
        given
     */

    @Override
    public TestFixture givenCommands(Object... commands) {
        Stream<Message> messages = asMessages(commands);
        return given(fc -> getDispatchResult(CompletableFuture.allOf(messages.map(
                c -> fc.commandGateway().send(c)).toArray(CompletableFuture[]::new))));
    }

    @Override
    public TestFixture givenCommandsByUser(User user, Object... commands) {
        return givenCommands(addUser(user, commands));
    }

    @Override
    public TestFixture givenAppliedEvents(String aggregateId, Object... events) {
        return given(fc -> applyEvents(aggregateId, fc, events, false));
    }

    @Override
    public TestFixture givenEvents(Object... events) {
        Stream<Message> messages = asMessages(events);
        return given(fc -> messages.forEach(c -> runSilently(
                () -> fc.eventGateway().publish(c, Guarantee.STORED).get())));
    }

    @Override
    public TestFixture givenDocument(Object document, String id, String collection, Instant timestamp, Instant end) {
        return given(fc -> fc.documentStore().index(document, id, collection, timestamp, end));
    }

    @Override
    public TestFixture givenDocuments(String collection, Object... documents) {
        return given(fc -> fc.documentStore().index(Arrays.asList(documents), collection));
    }

    @Override
    public TestFixture givenWebRequest(WebRequest webRequest) {
        return given(fc -> getDispatchResult(fc.webRequestGateway().send(webRequest)));
    }

    @Override
    public TestFixture givenTimeAdvancesTo(Instant instant) {
        return given(fc -> advanceTimeTo(instant));
    }

    @Override
    public TestFixture givenTimeElapses(Duration duration) {
        return given(fc -> advanceTimeBy(duration));
    }

    @Override
    public TestFixture given(Consumer<FluxCapacitor> condition) {
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
        Message message = trace(command);
        return whenApplying(fc -> getDispatchResult(fc.commandGateway().send(message)));
    }

    @Override
    public Then whenCommandByUser(Object command, User user) {
        return whenCommand(addUser(user, command)[0]);
    }

    @Override
    public Then whenQuery(Object query) {
        Message message = trace(query);
        return whenApplying(fc -> getDispatchResult(fc.queryGateway().send(message)));
    }

    @Override
    public Then whenQueryByUser(Object query, User user) {
        return whenQuery(addUser(user, query)[0]);
    }

    @Override
    public Then whenEvent(Object event) {
        Message message = trace(event);
        return when(fc -> runSilently(() -> fc.eventGateway().publish(message, Guarantee.STORED).get()));
    }

    @Override
    public Then whenEventsAreApplied(String aggregateId, Object... events) {
        return when(fc -> applyEvents(aggregateId, fc, events, true));
    }

    @Override
    public Then whenSearching(String collection, UnaryOperator<Search> searchQuery) {
        return whenApplying(fc -> searchQuery.apply(fc.documentStore().search(collection)).fetchAll());
    }

    @Override
    public Then whenWebRequest(WebRequest request) {
        return whenApplying(fc -> getDispatchResult(fc.webRequestGateway().send(trace(request))));
    }

    @Override
    public Then whenScheduleExpires(Object schedule) {
        Message message = trace(schedule);
        return when(fc -> fc.scheduler().schedule(message, getClock().instant()));
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
                    if (result instanceof Future<?>) {
                        try {
                            result = ((Future<?>) result).get(consumerTimeout.toMillis(), MILLISECONDS);
                        } catch (ExecutionException e) {
                            throw e.getCause();
                        }
                    }
                } catch (Throwable e) {
                    registerError(e);
                    result = e;
                }
                waitForConsumers();
                return getResultValidator(result, commands, events, schedules, getFutureSchedules(), errors);
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
                                      List<Schedule> schedules, List<Schedule> allSchedules,
                                      List<Throwable> errors) {
        return new ResultValidator(getFluxCapacitor(), result, events, commands, webRequests, schedules,
                                   allSchedules.stream().filter(
                                           s -> s.getDeadline().isAfter(getClock().instant())).collect(toList()),
                                   errors);
    }

    protected void applyEvents(String aggregateId, FluxCapacitor fc, Object[] events, boolean interceptBeforeStoring) {
        List<Message> eventList = asMessages(events).map(
                e -> e.withMetadata(e.getMetadata().with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, aggregateId)))
                .map(e -> interceptBeforeStoring ? e : interceptor.interceptDispatch(e, EVENT))
                .collect(toList());
        if (eventList.stream().anyMatch(e -> e.getPayload() instanceof Data<?>)) {
            for (Message event : eventList) {
                if (event.getPayload() instanceof Data<?>) {
                    Data<?> eventData = event.getPayload();
                    Data<byte[]> eventBytes = fc.serializer().serialize(eventData);
                    SerializedMessage message =
                            new SerializedMessage(eventBytes, event.getMetadata(), event.getMessageId(),
                                                  event.getTimestamp().toEpochMilli());
                    fc.client().getEventStoreClient().storeEvents(aggregateId, singletonList(message), false);
                } else {
                    fc.eventStore().storeEvents(aggregateId, event, false, interceptBeforeStoring);
                }
            }
        } else {
            fc.eventStore().storeEvents(aggregateId, eventList, false, interceptBeforeStoring);
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

    protected List<Schedule> getFutureSchedules() {
        SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
        if (schedulingClient instanceof InMemorySchedulingClient) {
            return ((InMemorySchedulingClient) schedulingClient).getSchedules(getFluxCapacitor().serializer())
                    .stream().filter(s -> s.getDeadline().isAfter(getClock().instant())).collect(toList());
        }
        return emptyList();
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
        ((TestClient) fluxCapacitor.client()).resetMocks();
        ((TestFluxCapacitor) fluxCapacitor).resetMocks();
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

    protected void registerWebRequest(Message request) {
        webRequests.add(request);
    }

    protected void registerSchedule(Schedule schedule) {
        schedules.add(schedule);
    }

    protected void registerError(Throwable e) {
        errors.addIfAbsent(e);
    }

    @SneakyThrows
    protected Object getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return synchronous
                    ? dispatchResult.get(0, MILLISECONDS)
                    : dispatchResult.get(resultTimeout.toMillis(), MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        } catch (TimeoutException e) {
            throw new TimeoutException("Test fixture did not receive a dispatch result in time. "
                                               + "Perhaps some messages did not have handlers?");
        }
    }

    protected Stream<Message> asMessages(Object... messages) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> Arrays.stream(messages).flatMap(c -> {
            if (c instanceof Collection<?>) {
                return ((Collection<?>) c).stream();
            }
            if (c.getClass().isArray()) {
                return Arrays.stream((Object[]) c);
            }
            return Stream.of(c);
        }).map(c -> parseObject(c, callerClass)).map(Message::asMessage));
    }

    protected Message trace(Object object) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> asMessage(parseObject(object, callerClass)).addMetadata(TRACE_TAG, "true"));
    }

    protected Object[] addUser(User user, Object... messages) {
        UserProvider userProvider = fluxCapacitor.userProvider();
        if (userProvider == null) {
            throw new IllegalStateException("UserProvider has not been configured");
        }
        return asMessages(messages).map(
                m -> m.withMetadata(userProvider.addToMetadata(m.getMetadata(), user))).toArray();
    }

    public static Object parseObject(Object object, Class<?> callerClass) {
        if (object instanceof String && ((String) object).endsWith(".json")) {
            return JsonUtils.fromFile(callerClass, (String) object);
        }
        return object;
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

        private final List<Schedule> publishedSchedules = new CopyOnWriteArrayList<>();

        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            if (!collectingResults) {
                message = message.addMetadata(IGNORE_TAG, "true");
            }

            DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
            if (currentMessage != null) {
                if (currentMessage.getMessageType() != SCHEDULE && currentMessage.getMetadata()
                        .containsKey(IGNORE_TAG)) {
                    message = message.addMetadata(IGNORE_TAG, "true");
                }
                if (currentMessage.getMetadata().containsKey(TRACE_TAG)) {
                    message = message.withMetadata(message.getMetadata().without(TRACE_TAG));
                }
            }

            if (messageType == SCHEDULE) {
                addMessage(publishedSchedules, (Schedule) message);
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
                    case WEBREQUEST:
                        registerWebRequest(message);
                        break;
                }
            }

            return message;
        }

        protected <T extends Message> void addMessage(List<T> messages, T message) {
            if (message instanceof Schedule) {
                messages.removeIf(m -> m instanceof Schedule && ((Schedule) m).getScheduleId()
                        .equals(((Schedule) message).getScheduleId()));
            }
            messages.add(message);
        }

        @Override
        public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
            List<Message> messages = consumers.computeIfAbsent(
                            tracker.getConfiguration(), c -> (c.getMessageType() == SCHEDULE
                            ? publishedSchedules : Collections.<Message>emptyList()).stream().filter(
                                    m -> Optional.ofNullable(c.getTypeFilter()).map(f -> m.getPayload().getClass()
                                            .getName().matches(f)).orElse(true))
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
                    registerError(e);
                    throw e;
                } finally {
                    if ((m.getMessageType() == COMMAND || m.getMessageType() == QUERY)
                        && isLocalHandler(handler.getTarget().getClass(), handler.getMethod(m))) {
                        synchronized (consumers) {
                            consumers.entrySet().stream()
                                    .filter(t -> t.getKey().getMessageType() == m.getMessageType())
                                    .forEach(e -> e.getValue().removeIf(
                                            m2 -> m2.getMessageId().equals(m.getMessageId())));
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
