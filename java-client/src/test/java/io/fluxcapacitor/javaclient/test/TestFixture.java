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
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
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
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.ScheduledCommandHandler;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSelf;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getHandleSelfAnnotation;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;
import static io.fluxcapacitor.javaclient.common.ClientUtils.runSilently;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
        return new TestFixture(fluxCapacitorBuilder, handlersFactory, InMemoryClient.newInstance(null), true);
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
        return new TestFixture(fluxCapacitorBuilder, handlersFactory, InMemoryClient.newInstance(null), false);
    }

    public static TestFixture createAsync(FluxCapacitorBuilder fluxCapacitorBuilder, Client client,
                                          Object... handlers) {
        return new TestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers), client, false);
    }

    public static Duration defaultResultTimeout = Duration.ofSeconds(10L);
    public static Duration defaultConsumerTimeout = Duration.ofSeconds(30L);

    @Getter
    private final FluxCapacitor fluxCapacitor;
    @Setter
    @Accessors(chain = true, fluent = true)
    private Duration resultTimeout = defaultResultTimeout;
    @Setter
    @Accessors(chain = true, fluent = true)
    private Duration consumerTimeout = defaultConsumerTimeout;
    private final boolean synchronous;
    private Registration registration = Registration.noOp();

    private volatile Message tracedMessage;
    private final Map<ActiveConsumer, List<Message>> consumers = new ConcurrentHashMap<>();
    private final List<Message> commands = new CopyOnWriteArrayList<>(), events = new CopyOnWriteArrayList<>(),
            webRequests = new CopyOnWriteArrayList<>(), webResponses = new CopyOnWriteArrayList<>(), metrics =
            new CopyOnWriteArrayList<>();
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
        List<Object> handlers = new ArrayList<>();
        if (synchronous) {
            fluxCapacitorBuilder.disableScheduledCommandHandler();
            handlers.add(new ScheduledCommandHandler());
        }
        GivenWhenThenInterceptor interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = new TestFluxCapacitor(
                fluxCapacitorBuilder.disableShutdownHook().addDispatchInterceptor(interceptor)
                        .replaceIdentityProvider(p -> p == IdentityProvider.defaultIdentityProvider
                                ? new PredictableIdFactory() : p)
                        .addBatchInterceptor(interceptor).addHandlerInterceptor(interceptor, true)
                        .build(new TestClient(client)));
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        handlers.addAll(handlerFactory.apply(fluxCapacitor));
        registerHandlers(handlers);
    }

    public TestFixture registerHandlers(Object... handlers) {
        return registerHandlers(Arrays.asList(handlers));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public TestFixture registerHandlers(List<?> handlers) {
        if (handlers.isEmpty()) {
            return this;
        }
        handlers.stream().collect(toMap(Object::getClass, Function.identity(), (a, b) -> {
            log.warn("Handler of type {} is registered more than once. Please make sure this is intentional.",
                     a.getClass());
            return a;
        }));
        if (!synchronous) {
            this.registration = getFluxCapacitor().registerHandlers(handlers);
            return this;
        }
        FluxCapacitor fluxCapacitor = getFluxCapacitor();
        HandlerFilter handlerFilter = (c, e) -> true;
        var registration = fluxCapacitor.apply(f -> handlers.stream().flatMap(h -> Stream
                        .of(fluxCapacitor.commandGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.queryGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.eventGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.eventStore().registerHandler(h, handlerFilter),
                            fluxCapacitor.errorGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.webRequestGateway().registerHandler(h, handlerFilter),
                            fluxCapacitor.metricsGateway().registerHandler(h, handlerFilter)))
                .reduce(Registration::merge).orElse(Registration.noOp()));
        if (fluxCapacitor.scheduler() instanceof DefaultScheduler scheduler) {
            registration.merge(fluxCapacitor.apply(fc -> handlers.stream().flatMap(h -> Stream
                            .of(scheduler.registerHandler(h, handlerFilter)))
                    .reduce(Registration::merge).orElse(Registration.noOp())));
        } else {
            log.warn("Could not register local schedule handlers");
        }
        this.registration = Optional.ofNullable(this.registration).map(r -> r.merge(registration)).orElse(registration);
        return this;
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

    @Override
    public TestFixture atFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

    public Clock getClock() {
        return getFluxCapacitor().clock();
    }

    /*
        given
     */

    @Override
    public TestFixture givenCommands(Object... commands) {
        Stream<Message> messages = asMessages(commands);
        given(fc -> messages.forEach(c -> getDispatchResult(fc.commandGateway().send(c))));
        return this;
    }

    @Override
    public TestFixture givenCommandsByUser(User user, Object... commands) {
        Stream<Message> messages = asMessages(commands).map(c -> addUser(user, c));
        given(fc -> messages.forEach(c -> getDispatchResult(fc.commandGateway().send(c))));
        return this;
    }

    @Override
    public TestFixture givenAppliedEvents(String aggregateId, Class<?> aggregateClass, Object... events) {
        Stream<Message> messages = asMessages(events);
        return given(fc -> applyEvents(aggregateId, aggregateClass, fc, messages.collect(toList())));
    }

    @Override
    public TestFixture givenEvents(Object... events) {
        Stream<Message> messages = asMessages(events);
        given(fc -> messages.toList().forEach(e -> fc.eventGateway().publish(e)));
        return this;
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
    public TestFixture givenTimeAdvancedTo(Instant instant) {
        return given(fc -> advanceTimeTo(instant));
    }

    @Override
    public TestFixture givenElapsedTime(Duration duration) {
        return given(fc -> advanceTimeBy(duration));
    }

    @Override
    public TestFixture given(Consumer<FluxCapacitor> condition) {
        return fluxCapacitor.apply(fc -> {
            try {
                handleExpiredSchedulesLocally();
                condition.accept(fc);
                try {
                    return this;
                } finally {
                    handleExpiredSchedulesLocally();
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
        return whenCommand(addUser(user, command));
    }

    @Override
    public Then whenQuery(Object query) {
        Message message = trace(query);
        return whenApplying(fc -> getDispatchResult(fc.queryGateway().send(message)));
    }

    @Override
    public Then whenQueryByUser(Object query, User user) {
        return whenQuery(addUser(user, query));
    }

    @Override
    public Then whenEvent(Object event) {
        Message message = trace(event);
        return whenExecuting(fc -> runSilently(() -> fc.eventGateway().publish(message, Guarantee.STORED).get()));
    }

    @Override
    public Then whenEventsAreApplied(String aggregateId, Class<?> aggregateClass, Object... events) {
        Stream<Message> messages = asMessages(events);
        return whenExecuting(fc -> applyEvents(aggregateId, aggregateClass, fc, messages.collect(toList())));
    }

    @Override
    public Then whenSearching(String collection, UnaryOperator<Search> searchQuery) {
        return whenApplying(fc -> searchQuery.apply(fc.documentStore().search(collection)).fetchAll());
    }

    @Override
    public Then whenWebRequest(WebRequest request) {
        return whenApplying(fc -> request.getMethod().isWebsocket()
                ? fc.webRequestGateway().sendAndForget(Guarantee.STORED, trace(request))
                : getDispatchResult(fc.webRequestGateway().send(trace(request))));
    }

    @Override
    public Then whenScheduleExpires(Object schedule) {
        Message message = trace(schedule);
        return whenExecuting(fc -> fc.scheduler().schedule(message, getCurrentTime()));
    }

    @Override
    @SneakyThrows
    public Then whenTimeElapses(Duration duration) {
        return whenExecuting(fc -> advanceTimeBy(duration));
    }

    @Override
    @SneakyThrows
    public Then whenTimeAdvancesTo(Instant instant) {
        return whenExecuting(fc -> advanceTimeTo(instant));
    }

    @Override
    public Then whenApplying(ThrowingFunction<FluxCapacitor, ?> action) {
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
                return getResultValidator(result, commands, events, schedules, getFutureSchedules(), errors, metrics);
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
                                      List<Throwable> errors, List<Message> metrics) {
        return new ResultValidator(getFluxCapacitor(), result, events, commands, webRequests, webResponses, metrics,
                                   schedules,
                                   allSchedules.stream().filter(
                                           s -> s.getDeadline().isAfter(getCurrentTime())).collect(toList()),
                                   errors);
    }

    protected void applyEvents(String aggregateId, Class<?> aggregateClass, FluxCapacitor fc, List<Message> events) {
        fc.aggregateRepository().load(aggregateId, aggregateClass).apply(events.stream().map(
                        e -> e.withMetadata(e.getMetadata().with(
                                Entity.AGGREGATE_ID_METADATA_KEY, aggregateId,
                                Entity.AGGREGATE_TYPE_METADATA_KEY, aggregateClass.getName())))
                .toList());
    }

    protected void handleExpiredSchedulesLocally() {
        if (synchronous) {
            SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
            if (schedulingClient instanceof InMemorySchedulingClient) {
                List<Schedule> expiredSchedules = ((InMemorySchedulingClient) schedulingClient)
                        .removeExpiredSchedules(getFluxCapacitor().serializer());
                if (getFluxCapacitor().scheduler() instanceof DefaultScheduler scheduler) {
                    expiredSchedules.forEach(scheduler::handleLocally);
                }
            }
        }
    }

    protected List<Schedule> getFutureSchedules() {
        SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
        if (schedulingClient instanceof InMemorySchedulingClient) {
            return ((InMemorySchedulingClient) schedulingClient).getSchedules(getFluxCapacitor().serializer())
                    .stream().filter(s -> s.getDeadline().isAfter(getCurrentTime())).collect(toList());
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
        advanceTimeTo(getCurrentTime().plus(duration));
    }

    protected void advanceTimeTo(Instant instant) {
        withClock(Clock.fixed(instant, ZoneId.systemDefault()));
    }

    protected void registerCommand(Message command) {
        commands.add(command);
    }

    protected void registerMetric(Message metric) {
        metrics.add(metric);
    }

    protected void registerEvent(Message event) {
        events.add(event);
    }

    protected void registerWebRequest(Message request) {
        webRequests.add(request);
    }

    protected void registerWebResponse(Message response) {
        webResponses.add(response);
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
            if (c == null) {
                return Stream.empty();
            }
            if (c instanceof Collection<?>) {
                return ((Collection<?>) c).stream();
            }
            if (c.getClass().isArray()) {
                return Arrays.stream((Object[]) c);
            }
            return Stream.of(c);
        }).flatMap(c -> {
            Object parsed = parsePayload(c, callerClass);
            return parsed == null ? Stream.empty()
                    : parsed instanceof Collection<?> ? ((Collection<?>) parsed).stream()
                    : parsed.getClass().isArray() ? Arrays.stream((Object[]) parsed)
                    : Stream.of(parsed);
        }).map(Message::asMessage));
    }

    protected Message trace(Object object) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return tracedMessage = fluxCapacitor.apply(fc -> asMessage(parsePayload(object, callerClass)));
    }

    public Message addUser(User user, Object value) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> asMessage(parsePayload(value, callerClass)).addUser(user));
    }

    public Object parsePayload(Object object, Class<?> callerClass) {
        object = parseObject(object, callerClass);
        if (object instanceof Data<?> data) {
            Data<byte[]> eventBytes = fluxCapacitor.serializer().serialize(data);
            object = fluxCapacitor.serializer().deserialize(eventBytes);
        }
        return object;
    }

    public static Object parseObject(Object object, Class<?> callerClass) {
        if (object instanceof String && ((String) object).endsWith(".json")) {
            object = JsonUtils.fromFile(callerClass, (String) object);
        }
        return object;
    }

    protected boolean checkConsumers() {
        if (synchronous) {
            return true;
        }
        synchronized (consumers) {
            if (consumers.values().stream().allMatch(l -> l.stream().allMatch(
                    m -> m instanceof Schedule && ((Schedule) m).getDeadline().isAfter(getCurrentTime())))) {
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
            if (messageType == SCHEDULE) {
                addMessage(publishedSchedules, (Schedule) message);
            }

            if (getHandleSelfAnnotation(message.getPayloadClass()).map(HandleSelf::logMessage).orElse(true)) {
                synchronized (consumers) {
                    consumers.entrySet().stream()
                            .filter(t -> {
                                var configuration = t.getKey();
                                return (configuration.getMessageType() == messageType && Optional
                                        .ofNullable(configuration.getTypeFilter())
                                        .map(f -> message.getPayload().getClass().getName().matches(f))
                                        .orElse(true));
                            }).forEach(e -> addMessage(e.getValue(), message));
                }
            }

            if (collectingResults
                && Optional.ofNullable(tracedMessage)
                        .map(t -> !Objects.equals(t.getMessageId(), message.getMessageId())).orElse(true)) {
                switch (messageType) {
                    case COMMAND -> registerCommand(message);
                    case EVENT -> registerEvent(message);
                    case SCHEDULE -> registerSchedule((Schedule) message);
                    case WEBREQUEST -> registerWebRequest(message);
                    case WEBRESPONSE -> registerWebResponse(message);
                    case METRICS -> registerMetric(message);
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
                    new ActiveConsumer(tracker.getConfiguration(), tracker.getMessageType()),
                    c -> (c.getMessageType() == SCHEDULE
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
                Function<DeserializingMessage, Object> function, HandlerInvoker invoker,
                String consumer) {
            return m -> {
                try {
                    return function.apply(m);
                } catch (Exception e) {
                    registerError(e);
                    throw e;
                } finally {
                    if (m.getMessageType().isRequest()
                        && getLocalHandlerAnnotation(invoker.getTarget().getClass(), invoker.getMethod())
                                .map(l -> !l.logMessage()).orElse(false)) {
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
            consumers.remove(new ActiveConsumer(tracker.getConfiguration(), tracker.getMessageType()));
            checkConsumers();
        }
    }

    @Value
    protected static class ActiveConsumer {
        @Delegate
        ConsumerConfiguration configuration;
        MessageType messageType;
    }
}
