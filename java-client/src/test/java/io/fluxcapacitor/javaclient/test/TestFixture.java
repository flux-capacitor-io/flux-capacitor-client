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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.application.SimplePropertySource;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
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
import io.fluxcapacitor.javaclient.scheduling.ScheduledCommand;
import io.fluxcapacitor.javaclient.scheduling.client.LocalSchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpCookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.SCHEDULE;
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
@Getter(AccessLevel.PACKAGE)
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
    private final FluxCapacitorBuilder fluxCapacitorBuilder;
    private final GivenWhenThenInterceptor interceptor;
    private Duration resultTimeout = defaultResultTimeout;
    private Duration consumerTimeout = defaultConsumerTimeout;
    private final boolean synchronous;
    private final boolean spying;
    private Registration registration = Registration.noOp();

    private final Map<ActiveConsumer, List<Message>> consumers = new ConcurrentHashMap<>();

    @Delegate
    private FixtureResult fixtureResult = new FixtureResult();

    private final BeanParameterResolver beanParameterResolver = new BeanParameterResolver();
    private final Map<String, String> testProperties = new HashMap<>();
    private final List<HttpCookie> cookies = new ArrayList<>();

    private final List<ThrowingConsumer<TestFixture>> modifiers = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<List<TestFixture>> activeFixtures = ThreadLocal.withInitial(ArrayList::new);
    private static final Executor shutdownExecutor = Executors.newFixedThreadPool(16);

    public static void shutDownActiveFixtures() {
        var fixtures = activeFixtures.get();
        if (!fixtures.isEmpty()) {
            activeFixtures.remove();
            fixtures.forEach(fixture -> shutdownExecutor.execute(() -> {
                Optional.ofNullable(fixture.registration).ifPresent(Registration::cancel);
                fixture.fluxCapacitor.client().shutDown();
            }));
        }
    }

    protected TestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                          Function<FluxCapacitor, List<?>> handlerFactory, Client client, boolean synchronous) {
        activeFixtures.get().add(this);
        this.synchronous = synchronous;
        this.spying = false;
        Optional<TestUserProvider> userProvider =
                Optional.ofNullable(UserProvider.defaultUserSupplier).map(TestUserProvider::new);
        if (userProvider.isPresent()) {
            fluxCapacitorBuilder = fluxCapacitorBuilder.registerUserProvider(userProvider.get());
        }
        if (synchronous) {
            fluxCapacitorBuilder.disableScheduledCommandHandler();
        }
        fluxCapacitorBuilder.addPropertySource(new SimplePropertySource(testProperties));
        this.interceptor = new GivenWhenThenInterceptor(this);
        Arrays.stream(MessageType.values()).forEach(type -> client.getGatewayClient(type).registerMonitor(
                messages -> interceptor.interceptClientDispatch(messages, type)));
        fluxCapacitorBuilder = fluxCapacitorBuilder.disableShutdownHook()
                .addParameterResolver(beanParameterResolver)
                .addDispatchInterceptor(interceptor)
                .replaceIdentityProvider(p -> p == IdentityProvider.defaultIdentityProvider
                        ? PredictableIdentityProvider.defaultPredictableIdentityProvider() : p)
                .addBatchInterceptor(interceptor).addHandlerInterceptor(interceptor, true);
        this.fluxCapacitorBuilder = fluxCapacitorBuilder;
        this.fluxCapacitor = fluxCapacitorBuilder.build(client);
        if (synchronous) {
            localHandlerRegistries(fluxCapacitor).forEach(r -> r.setSelfHandlerFilter(HandlerFilter.ALWAYS_HANDLE));
        }
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        List<Object> handlers = new ArrayList<>();
        if (synchronous) {
            handlers.add(new Object() {
                @HandleSchedule
                void handle(ScheduledCommand schedule) {
                    SerializedMessage command = schedule.getCommand();
                    command.setTimestamp(FluxCapacitor.currentTime().toEpochMilli());
                    fluxCapacitor.serializer()
                            .deserializeMessages(Stream.of(command), MessageType.COMMAND).findFirst().map(
                                    DeserializingMessage::toMessage).ifPresent(FluxCapacitor::sendAndForgetCommand);
                }
            });
        }
        handlers.addAll(handlerFactory.apply(fluxCapacitor));
        registerHandlers(handlers);
    }

    protected TestFixture(TestFixture currentFixture, boolean synchronous, boolean spying) {
        shutDownActiveFixtures();
        activeFixtures.get().add(this);
        this.synchronous = synchronous;
        this.spying = spying;
        this.fluxCapacitorBuilder = currentFixture.fluxCapacitorBuilder;
        (this.interceptor = currentFixture.interceptor).testFixture = this;
        var currentClient = currentFixture.fluxCapacitor.client().unwrap();
        var newClient = currentClient instanceof InMemoryClient
                ? InMemoryClient.newInstance(null) : currentClient;
        {
            Arrays.stream(MessageType.values())
                    .forEach(type -> newClient.getGatewayClient(type).registerMonitor(
                            messages -> interceptor.interceptClientDispatch(messages, type)));
        }
        this.fluxCapacitor = spying
                ? new TestFluxCapacitor(fluxCapacitorBuilder.build(new TestClient(newClient)))
                : fluxCapacitorBuilder.build(newClient);
        localHandlerRegistries(this.fluxCapacitor).forEach(r -> r.setSelfHandlerFilter(
                synchronous ? HandlerFilter.ALWAYS_HANDLE : (t, m) -> !ClientUtils.isSelfTracking(t, m)));
        currentFixture.modifiers.forEach(this::modifyFixture);
    }

    /*
        Modifications
     */

    /**
     * Sets the maximum duration this test fixture will wait for a response of a request passed in the given- or
     * when-phase.
     * <p>
     * This is only relevant if the test fixture is asynchronous.
     */
    public TestFixture resultTimeout(Duration resultTimeout) {
        return modifyFixture(fixture -> fixture.resultTimeout = resultTimeout);
    }

    /**
     * Sets the maximum duration this test fixture will wait for a consumer to finish handling messages dispatched
     * during the given- or when-phase.
     * <p>
     * This is only relevant if the test fixture is asynchronous.
     */
    public TestFixture consumerTimeout(Duration consumerTimeout) {
        return modifyFixture(fixture -> fixture.consumerTimeout = consumerTimeout);
    }

    /**
     * Returns an asynchronous version of this test fixture. If the current fixture is asynchronous already, it is
     * returned unmodified.
     * <p>
     * The returned test fixture will have a nearly identical state, i.e. it will have the same handlers, clock,
     * properties and 'given' conditions as the source fixture.
     */
    public TestFixture async() {
        return synchronous ? new TestFixture(this, false, spying) : this;
    }

    /**
     * Returns a synchronous version of this test fixture. If the current fixture is synchronous already, it is returned
     * unmodified.
     * <p>
     * The returned test fixture will have a nearly identical state, i.e. it will have the same handlers, clock,
     * properties and 'given' conditions as the source fixture.
     */
    public TestFixture sync() {
        return !synchronous ? new TestFixture(this, true, spying) : this;
    }

    /**
     * Returns a version of this test fixture in which Flux components like e.g.
     * {@link io.fluxcapacitor.javaclient.publishing.EventGateway} and
     * {@link io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient} are Mockito spies.
     * <p>
     * The returned test fixture will have a nearly identical state, i.e. it will have the same handlers, clock,
     * properties and 'given' conditions as the source fixture.
     *
     * @see org.mockito.Mockito#spy(Object[])
     */
    public TestFixture spy() {
        return spying ? this : new TestFixture(this, synchronous, true);
    }

    /**
     * Register additional handlers with the test fixture.
     * <p>
     * For async test fixtures, make sure all handlers of the same consumer are registered together, i.e. either via one
     * of the test fixture creator methods, or all at the same time via registerHandlers. If handlers that share the
     * same consumer are registered separately, an exception will be raised.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public TestFixture registerHandlers(List<?> handlers) {
        return modifyFixture(fixture -> {
            FluxCapacitor fc = fixture.getFluxCapacitor();
            if (handlers.isEmpty()) {
                return;
            }
            handlers.stream().collect(toMap(o -> o instanceof Class<?> c ? c : o instanceof Handler<?> h
                                                    ? h.getTargetClass() : o.getClass(),
                                            Function.identity(), (a, b) -> {
                        log.warn(
                                "Handler of type {} is registered more than once. Please make sure this is intentional.",
                                a.getClass());
                        return a;
                    }));
            if (!fixture.synchronous) {
                fixture.registration = fixture.registration.merge(fc.registerHandlers(handlers));
                return;
            }
            HandlerFilter handlerFilter = (c, e) -> true;
            var registration = fc.apply(f -> handlers.stream().flatMap(
                            h -> localHandlerRegistries(f).map(r -> r.registerHandler(h, handlerFilter)))
                    .reduce(Registration::merge).orElse(Registration.noOp()));
            fixture.registration = fixture.registration.merge(registration);
        });
    }

    protected Stream<HasLocalHandlers> localHandlerRegistries(FluxCapacitor fluxCapacitor) {
        var gateways = Stream.of(
                fluxCapacitor.commandGateway(),
                fluxCapacitor.queryGateway(),
                fluxCapacitor.eventGateway(),
                fluxCapacitor.eventStore(),
                fluxCapacitor.errorGateway(),
                fluxCapacitor.webRequestGateway(),
                fluxCapacitor.metricsGateway());
        return fluxCapacitor.scheduler() instanceof DefaultScheduler scheduler ?
                Stream.concat(gateways, Stream.of(scheduler)) : gateways;
    }

    /**
     * Register additional handlers with the test fixture.
     * <p>
     * For async test fixtures, make sure all handlers of the same consumer are registered together, i.e. either via one
     * of the test fixture creator methods, or all at the same time via registerHandlers. If handlers that share the
     * same consumer are registered separately, an exception will be raised.
     */
    public TestFixture registerHandlers(Object... handlers) {
        return registerHandlers(Arrays.asList(handlers));
    }

    @Override
    public TestFixture withClock(Clock clock) {
        return modifyFixture(fixture -> fixture.setClock(clock));
    }

    @Override
    public TestFixture atFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

    @Override
    public TestFixture withProperty(String name, Object value) {
        return modifyFixture(
                fixture -> fixture.testProperties.compute(name, (k, v) -> value == null ? null : value.toString()));
    }

    @Override
    public TestFixture withBean(Object bean) {
        return modifyFixture(fixture -> fixture.beanParameterResolver.registerBean(bean));
    }

    protected TestFixture modifyFixture(ThrowingConsumer<TestFixture> modifier) {
        modifiers.add(modifier);
        return fluxCapacitor.apply(fc -> {
            modifier.accept(this);
            return this;
        });
    }

    /*
        given
     */

    @Override
    public TestFixture givenCommands(Object... commands) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        for (Object command : commands) {
            givenModification(fixture -> fixture.asMessages(callerClass, command).forEach(
                    c -> fixture.getDispatchResult(fixture.getFluxCapacitor().commandGateway().send(c))));
        }
        return this;
    }

    @Override
    public TestFixture givenCommandsByUser(Object userRep, Object... commands) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        for (Object command : commands) {
            givenModification(
                    fixture -> fixture.asMessages(callerClass, command).map(c -> fixture.addUser(getUser(userRep), c))
                            .forEach(c -> fixture.getDispatchResult(
                                    fixture.getFluxCapacitor().commandGateway().send(c))));
        }
        return this;
    }

    @Override
    public TestFixture givenAppliedEvents(String aggregateId, Class<?> aggregateClass, Object... events) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return givenModification(fixture -> fixture.applyEvents(aggregateId, aggregateClass, fixture.getFluxCapacitor(),
                                                                fixture.asMessages(callerClass, events).toList()));
    }

    @Override
    public TestFixture givenEvents(Object... events) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        for (Object event : events) {
            givenModification(fixture -> fixture.asMessages(callerClass, event)
                    .forEach(e -> fixture.getFluxCapacitor().eventGateway().publish(e)));
        }
        return this;
    }

    @Override
    public TestFixture givenDocument(Object document, Object id, Object collection, Instant timestamp, Instant end) {
        return givenModification(fixture -> fixture.getFluxCapacitor().documentStore()
                .index(document, id, collection, timestamp, end).get());
    }

    @Override
    public TestFixture givenDocument(Object document) {
        givenModification(fixture -> fixture.getFluxCapacitor().documentStore().index(document).get());
        return this;
    }

    @Override
    public TestFixture givenDocuments(Object collection, Object firstDocument, Object... otherDocuments) {
        for (Object document : Stream.concat(Stream.of(firstDocument), Arrays.stream(otherDocuments)).toList()) {
            givenModification(fixture -> fixture.getFluxCapacitor().documentStore().index(document, collection).get());
        }
        return this;
    }

    @Override
    public TestFixture givenWebRequest(WebRequest webRequest) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return givenModification(fixture -> {
            WebRequest request = fixture.parseObject(webRequest, callerClass);
            fixture.executeWebRequest(request);
        });
    }

    @SneakyThrows
    protected Object executeWebRequest(WebRequest request) {
        if (!cookies.isEmpty()) {
            var builder = request.toBuilder();
            for (HttpCookie cookie : cookies) {
                if (request.getCookie(cookie.getName()).isEmpty()) {
                    builder.cookie(cookie);
                }
            }
            request = builder.build();
        }
        if (request.getMethod().isWebsocket()) {
            return fluxCapacitor.webRequestGateway().sendAndForget(Guarantee.STORED, request).get();
        }
        WebResponse response = getDispatchResult(getFluxCapacitor().webRequestGateway().send(request));
        response.getCookies().forEach(cookie -> {
            cookies.remove(cookie);
            if (!cookie.hasExpired()) {
                cookies.add(cookie);
            }
        });
        return response;
    }

    @Override
    public TestFixture givenTimeAdvancedTo(Instant instant) {
        return givenModification(fixture -> fixture.advanceTimeTo(instant));
    }

    @Override
    public TestFixture givenElapsedTime(Duration duration) {
        return givenModification(fixture -> fixture.advanceTimeBy(duration));
    }

    @Override
    public TestFixture given(ThrowingConsumer<FluxCapacitor> condition) {
        return givenModification(fixture -> condition.accept(fixture.getFluxCapacitor()));
    }

    protected TestFixture givenModification(ThrowingConsumer<TestFixture> modifier) {
        return modifyFixture(fixture -> {
            try {
                fixture.handleExpiredSchedulesLocally(false);
                modifier.accept(fixture);
                fixture.handleExpiredSchedulesLocally(false);
                fixture.waitForConsumers();
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to execute given", e);
            }
        });
    }

    /*
        when
     */

    @Override
    public Then<Object> whenCommand(Object command) {
        Message message = trace(command);
        return whenApplying(fc -> getDispatchResult(fc.commandGateway().send(message)));
    }

    @Override
    public Then<Object> whenCommandByUser(Object user, Object command) {
        Message message = trace(command);
        return whenApplying(fc -> getDispatchResult(fc.commandGateway().send(addUser(getUser(user), message))));
    }

    @Override
    public Then<Object> whenQuery(Object query) {
        Message message = trace(query);
        return whenApplying(fc -> getDispatchResult(fc.queryGateway().send(message)));
    }

    @Override
    public Then<Object> whenQueryByUser(Object user, Object query) {
        Message message = trace(query);
        return whenApplying(fc -> getDispatchResult(fc.queryGateway().send(addUser(getUser(user), message))));
    }

    @Override
    public Then<?> whenEvent(Object event) {
        Message message = trace(event);
        return whenExecuting(fc -> runSilently(() -> fc.eventGateway().publish(message, Guarantee.STORED).get()));
    }

    @Override
    public Then<?> whenEventsAreApplied(String aggregateId, Class<?> aggregateClass, Object... events) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return whenExecuting(fc -> applyEvents(aggregateId, aggregateClass, fc,
                                               asMessages(callerClass, events).collect(toList())));
    }

    @Override
    public <R> Then<List<R>> whenSearching(Object collection, UnaryOperator<Search> searchQuery) {
        return whenApplying(fc -> searchQuery.apply(fc.documentStore().search(collection)).fetchAll());
    }

    @Override
    public Then<Object> whenWebRequest(WebRequest request) {
        WebRequest message = trace(request);
        return whenApplying(fc -> executeWebRequest(message));
    }

    @Override
    public Then<?> whenScheduleExpires(Object schedule) {
        Message message = trace(schedule);
        return whenExecuting(fc -> fc.scheduler().schedule(message, getCurrentTime()));
    }

    @Override
    @SneakyThrows
    public Then<?> whenTimeElapses(Duration duration) {
        return whenExecuting(fc -> advanceTimeBy(duration));
    }

    @Override
    @SneakyThrows
    public Then<?> whenTimeAdvancesTo(Instant instant) {
        return whenExecuting(fc -> advanceTimeTo(instant));
    }

    @Override
    public <R> Then<R> whenApplying(ThrowingFunction<FluxCapacitor, R> action) {
        return fluxCapacitor.apply(fc -> {
            handleExpiredSchedulesLocally(true);
            waitForConsumers();
            resetMocks();
            setCollectingResults(true);
            Object result;
            try {
                result = action.apply(fc);
                if (result instanceof CompletableFuture<?> future) {
                    result = getDispatchResult(future);
                }
            } catch (Throwable e) {
                registerError(e);
                result = e;
            }
            setResult(result);
            waitForConsumers();
            handleExpiredSchedulesLocally(true);
            return new ResultValidator<>(this);
        });
    }

    /*
        helper
     */

    protected User getUser(Object userOrId) {
        User result = userOrId instanceof User user ? user
                : fluxCapacitor.apply(fc -> fc.userProvider().getUserById(userOrId));
        if (result == null) {
            throw new UnauthorizedException("User %s could not be provided".formatted(userOrId));
        }
        return result;
    }

    protected void applyEvents(String aggregateId, Class<?> aggregateClass, FluxCapacitor fc, List<Message> events) {
        fc.aggregateRepository().load(aggregateId, aggregateClass).apply(events.stream().map(
                        e -> e.withMetadata(e.getMetadata().with(
                                Entity.AGGREGATE_ID_METADATA_KEY, aggregateId,
                                Entity.AGGREGATE_TYPE_METADATA_KEY, aggregateClass.getName())))
                                                                                 .toList());
    }

    protected void handleExpiredSchedulesLocally(boolean collectErrors) {
        if (synchronous) {
            try {
                SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
                if (schedulingClient instanceof LocalSchedulingClient local) {
                    List<Schedule> expiredSchedules = local.removeExpiredSchedules(getFluxCapacitor().serializer());
                    if (getFluxCapacitor().scheduler() instanceof DefaultScheduler scheduler) {
                        expiredSchedules.forEach(scheduler::handleLocally);
                    }
                }
            } catch (Throwable e) {
                if (collectErrors) {
                    registerError(e);
                } else {
                    throw e;
                }
            }
        }
    }

    protected List<Schedule> getFutureSchedules() {
        return getFluxCapacitor().client().getSchedulingClient() instanceof LocalSchedulingClient local ?
                local.getSchedules(getFluxCapacitor().serializer()) : emptyList();
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

    protected TestFixture reset() {
        resetMocks();
        fixtureResult = new FixtureResult();
        return this;
    }

    protected void resetMocks() {
        if (spying) {
            ((TestClient) fluxCapacitor.client()).resetMocks();
            ((TestFluxCapacitor) fluxCapacitor).resetMocks();
        }
    }

    protected void advanceTimeBy(Duration duration) {
        advanceTimeTo(getCurrentTime().plus(duration));
    }

    protected void advanceTimeTo(Instant instant) {
        setClock(Clock.fixed(instant, ZoneId.systemDefault()));
    }

    protected void setClock(Clock clock) {
        getFluxCapacitor().withClock(clock);
        SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
        if (schedulingClient instanceof LocalSchedulingClient local) {
            local.setClock(clock);
        } else {
            log.warn("Could not update clock of scheduling client. Timing tests may not work.");
        }
    }

    protected void registerCommand(Message command) {
        getCommands().add(command);
    }

    protected void registerQuery(Message query) {
        getQueries().add(query);
    }

    protected void registerMetric(Message metric) {
        getMetrics().add(metric);
    }

    protected void registerEvent(Message event) {
        getEvents().add(event);
    }

    protected void registerWebRequest(Message request) {
        getWebRequests().add(request);
    }

    protected void registerWebResponse(Message response) {
        getWebResponses().add(response);
    }

    protected void registerSchedule(Schedule schedule) {
        getSchedules().add(schedule);
    }

    protected void registerError(Throwable e) {
        getErrors().addIfAbsent(e);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    protected <R> R getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return (R) (synchronous
                    ? dispatchResult.get(0, MILLISECONDS)
                    : dispatchResult.get(resultTimeout.toMillis(), MILLISECONDS));
        } catch (ExecutionException e) {
            throw e.getCause();
        } catch (TimeoutException e) {
            throw new TimeoutException("Test fixture did not receive a dispatch result in time. "
                                       + "Perhaps some messages did not have handlers?");
        }
    }

    protected Stream<Message> asMessages(Class<?> callerClass, Object... messages) {
        return Arrays.stream(messages).flatMap(c -> {
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
            Object parsed = parseObject(c, callerClass);
            return parsed == null ? Stream.empty()
                    : parsed instanceof Collection<?> ? ((Collection<?>) parsed).stream()
                    : parsed.getClass().isArray() ? Arrays.stream((Object[]) parsed)
                    : Stream.of(parsed);
        }).map(Message::asMessage);
    }

    @SuppressWarnings("unchecked")
    protected <M extends Message> M trace(Object object) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        M result = (M) fluxCapacitor.apply(fc -> asMessage(parseObject(object, callerClass)));
        setTracedMessage(result);
        return result;
    }

    protected Message addUser(User user, Object value) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> asMessage(parseObject(value, callerClass)).addUser(user));
    }

    @SuppressWarnings("unchecked")
    public <T> T parseObject(T object, Class<?> callerClass) {
        if (object instanceof Message message) {
            return (T) message.withPayload(parseObject(message.getPayload(), callerClass));
        }
        if (object instanceof String && ((String) object).endsWith(".json")) {
            object = JsonUtils.fromFile(callerClass, (String) object);
        }
        if (object instanceof SerializedObject<?, ?> s) {
            SerializedObject<byte[], ?> eventBytes = s.data().getValue() instanceof byte[]
                    ? (SerializedObject<byte[], ?>) s : fluxCapacitor.serializer().serialize(s);
            object = fluxCapacitor.serializer().deserialize(eventBytes);
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

    @AllArgsConstructor
    protected static class GivenWhenThenInterceptor implements DispatchInterceptor, BatchInterceptor, HandlerInterceptor {
        private TestFixture testFixture;

        private final List<Schedule> publishedSchedules = new CopyOnWriteArrayList<>();
        private final Set<String> interceptedMessageIds = new CopyOnWriteArraySet<>();

        protected void interceptClientDispatch(List<SerializedMessage> messages, MessageType messageType) {
            if (testFixture.isCollectingResults()) {
                try {
                    testFixture.fluxCapacitor.serializer()
                            .deserializeMessages(messages.stream()
                                                         .filter(m -> !interceptedMessageIds.contains(
                                                                 m.getMessageId())),
                                                 messageType)
                            .map(DeserializingMessage::toMessage)
                            .forEach(m -> monitorDispatch(m, messageType));
                } catch (Exception ignored) {
                    log.warn("Failed to intercept a published message. This may cause your test to fail.");
                }
            }
        }

        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            return message;
        }

        @Override
        public void monitorDispatch(Message message, MessageType messageType) {
            if (testFixture.isCollectingResults()) {
                interceptedMessageIds.add(message.getMessageId());
            }

            if (messageType == SCHEDULE) {
                addMessage(publishedSchedules, (Schedule) message);
            }

            synchronized (testFixture.consumers) {
                testFixture.consumers.entrySet().stream()
                        .filter(t -> {
                            var configuration = t.getKey();
                            return (configuration.getMessageType() == messageType && Optional
                                    .ofNullable(configuration.getTypeFilter())
                                    .map(f -> message.getPayload().getClass().getName().matches(f))
                                    .orElse(true));
                        }).forEach(e -> addMessage(e.getValue(), message));
            }

            if (captureMessage(message)) {
                switch (messageType) {
                    case COMMAND -> testFixture.registerCommand(message);
                    case QUERY -> testFixture.registerQuery(message);
                    case EVENT -> testFixture.registerEvent(message);
                    case SCHEDULE -> testFixture.registerSchedule((Schedule) message);
                    case WEBREQUEST -> testFixture.registerWebRequest(message);
                    case WEBRESPONSE -> testFixture.registerWebResponse(message);
                    case METRICS -> testFixture.registerMetric(message);
                }
            }
        }

        protected Boolean captureMessage(Message message) {
            return testFixture.isCollectingResults()
                   && Optional.ofNullable(testFixture.getFixtureResult().getTracedMessage())
                    .map(t -> !Objects.equals(t.getMessageId(), message.getMessageId())).orElse(true);
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
            List<Message> messages = testFixture.consumers.computeIfAbsent(
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
                synchronized (testFixture.consumers) {
                    messages.removeIf(m -> messageIds.contains(m.getMessageId()));
                    testFixture.checkConsumers();
                }
            };
        }

        @Override
        public Function<DeserializingMessage, Object> interceptHandling(
                Function<DeserializingMessage, Object> function, HandlerInvoker invoker) {
            return m -> {
                try {
                    return function.apply(m);
                } catch (Exception e) {
                    testFixture.registerError(e);
                    throw e;
                } finally {
                    if (
                            m.getMessageType().isRequest()
                            && Tracker.current().map(Tracker::getMessageBatch).map(batch -> batch.getMessages().stream()
                                            .noneMatch(bm -> bm.getMessageId().equals(m.getMessageId())))
                                    .orElse(true)
                            && getLocalHandlerAnnotation(
                                    invoker.getTargetClass(), invoker.getMethod())
                                    .map(l -> !l.logMessage()).orElse(true)
                    ) {
                        synchronized (testFixture.consumers) {
                            testFixture.consumers.entrySet().stream()
                                    .filter(t -> t.getKey().getMessageType() == m.getMessageType())
                                    .forEach(e -> e.getValue().removeIf(
                                            m2 -> m2.getMessageId().equals(m.getMessageId())));
                        }
                        testFixture.checkConsumers();
                    }
                }
            };
        }

        @Override
        public void shutdown(Tracker tracker) {
            testFixture.consumers.remove(new ActiveConsumer(tracker.getConfiguration(), tracker.getMessageType()));
            testFixture.checkConsumers();
        }
    }

    @Value
    protected static class ActiveConsumer {
        @Delegate
        ConsumerConfiguration configuration;
        MessageType messageType;
    }
}
