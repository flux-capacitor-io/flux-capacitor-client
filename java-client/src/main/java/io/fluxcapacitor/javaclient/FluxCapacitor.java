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

package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.UuidFactory;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.WebRequestGateway;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.modeling.AggregateRoot.getAggregateId;
import static java.util.Arrays.stream;

/**
 * High-level client for Flux Capacitor. If you are using anything other than this to interact with the service at
 * runtime you're probably doing it wrong.
 * <p>
 * To start handling messages build an instance of this API and invoke {@link #registerHandlers}.
 * <p>
 * Once you are handling messages you can simply use the static methods provided (e.g. to publish messages etc). In
 * those cases it is not necessary to inject an instance of this API. This minimizes the need for dependencies in your
 * functional classes and maximally cashes in on location transparency.
 * <p>
 * To build an instance of this client check out {@link DefaultFluxCapacitor}.
 */
public interface FluxCapacitor extends AutoCloseable {

    /**
     * Flux Capacitor instance set by the current application. Used as a fallback when no threadlocal instance was set.
     * This is added as a convenience for applications that never have more than one than FluxCapacitor instance which
     * will be the case for nearly all applications. On application startup simply fill this application instance.
     */
    AtomicReference<FluxCapacitor> applicationInstance = new AtomicReference<>();

    /**
     * Flux Capacitor instance bound to the current thread. Normally there's only one FluxCapacitor client per
     * application. Before messages are passed to message handlers the FluxCapacitor client binds itself to this field.
     * By doing so message handlers can interact with Flux Capacitor without injecting any dependencies.
     */
    ThreadLocal<FluxCapacitor> instance = new ThreadLocal<>();

    /**
     * Returns the Flux Capacitor instance bound to the current thread or else set by the current application. Throws an
     * exception if no instance was registered.
     */
    static FluxCapacitor get() {
        return Optional.ofNullable(instance.get())
                .orElseGet(() -> Optional.ofNullable(applicationInstance.get())
                        .orElseThrow(() -> new IllegalStateException("FluxCapacitor instance not set")));
    }

    /**
     * Returns the FluxCapacitor client bound to the current thread or else set by the current application as Optional.
     * Returns an empty Optional if no instance was registered.
     */
    static Optional<FluxCapacitor> getOptionally() {
        FluxCapacitor result = instance.get();
        return result == null ? Optional.ofNullable(applicationInstance.get()) : Optional.of(result);
    }

    /**
     * Gets the clock of the current FluxCapacitor instance (obtained via {@link #getOptionally()}). If there is no
     * current instance the system's UTC clock is returned.
     */
    static Clock currentClock() {
        return getOptionally().map(FluxCapacitor::clock).orElseGet(Clock::systemUTC);
    }

    /**
     * Gets the {@link IdentityProvider} of the current FluxCapacitor to generate a unique identifier. If there is no
     * current FluxCapacitor instance a new UUID is generated.
     */
    static String generateId() {
        return currentIdentityProvider().nextFunctionalId();
    }

    /**
     * Fetches the {@link IdentityProvider} of the current FluxCapacitor. If there is no current FluxCapacitor instance
     * a new UUID factory is generated.
     */
    static IdentityProvider currentIdentityProvider() {
        return getOptionally().map(FluxCapacitor::identityProvider).orElseGet(UuidFactory::new);
    }

    /**
     * Gets the current correlation data, which by default depends on the current {@link Client}, {@link Tracker} and
     * {@link DeserializingMessage}
     */
    static Map<String, String> currentCorrelationData() {
        return getOptionally().map(FluxCapacitor::correlationDataProvider).orElse(
                DefaultCorrelationDataProvider.INSTANCE).getCorrelationData();
    }

    /**
     * Publishes the given application event. The event may be an instance of a {@link Message} in which case it will be
     * published as is. Otherwise the event is published using the passed value as payload without additional metadata.
     * <p>
     * Note that the published event will not be available for event sourcing as it is does not belong to any
     * aggregate.
     *
     * @see #aggregateRepository() if you're interested in publishing events that belong to an aggregate.
     */
    static void publishEvent(Object event) {
        get().eventGateway().publish(event);
    }

    /**
     * Publishes an event with given payload and metadata.
     *
     * @see #publishEvent(Object) for more info
     */
    static void publishEvent(Object payload, Metadata metadata) {
        get().eventGateway().publish(payload, metadata);
    }

    /**
     * Sends the given command and doesn't wait for a result. The command may be an instance of a {@link Message} in
     * which case it will be sent as is. Otherwise the command is published using the passed value as payload without
     * additional metadata.
     *
     * @see #sendCommand(Object) to send a command and inspect its result asynchronously
     */
    static void sendAndForgetCommand(Object command) {
        get().commandGateway().sendAndForget(command);
    }

    /**
     * Sends the given commands and doesn't wait for results. Commands may be an instance of a {@link Message} in which
     * case it will be sent as is. Otherwise, the commands are published using the passed value as payload without
     * additional metadata.
     *
     * @see #sendCommands(Object...)  to send commands and inspect their results asynchronously
     */
    static void sendAndForgetCommands(Object... commands) {
        get().commandGateway().sendAndForget(commands);
    }

    /**
     * Sends a command with given payload and metadata and don't wait for a result.
     *
     * @see #sendCommand(Object, Metadata) to send a command and inspect its result
     */
    static void sendAndForgetCommand(Object payload, Metadata metadata) {
        get().commandGateway().sendAndForget(payload, metadata);
    }

    /**
     * Sends a command with given payload and metadata and don't wait for a result. With a guarantee the method will
     * wait for the command itself to be sent or stored.
     *
     * @see #sendCommand(Object, Metadata) to send a command and inspect its result
     */
    static void sendAndForgetCommand(Object payload, Metadata metadata, Guarantee guarantee) {
        get().commandGateway().sendAndForget(payload, metadata, guarantee);
    }


    /**
     * Sends the given command and returns a future that will be completed with the command's result. The command may be
     * an instance of a {@link Message} in which case it will be sent as is. Otherwise the command is published using
     * the passed value as payload without additional metadata.
     */
    static <R> CompletableFuture<R> sendCommand(Object command) {
        return get().commandGateway().send(command);
    }

    /**
     * Sends the given commands and returns a list of futures that will be completed with the commands' results. The
     * commands may be instances of a {@link Message} in which case they will be sent as is. Otherwise, the commands are
     * published using the passed values as payload without additional metadata.
     */
    static <R> List<CompletableFuture<R>> sendCommands(Object... commands) {
        return get().commandGateway().send(commands);
    }

    /**
     * Sends a command with given payload and metadata and returns a future that will be completed with the command's
     * result.
     */
    static <R> CompletableFuture<R> sendCommand(Object payload, Metadata metadata) {
        return get().commandGateway().send(payload, metadata);
    }

    /**
     * Sends the given command and returns the command's result. The command may be an instance of a {@link Message} in
     * which case it will be sent as is. Otherwise the command is published using the passed value as payload without
     * additional metadata.
     */
    static <R> R sendCommandAndWait(Object command) {
        return get().commandGateway().sendAndWait(command);
    }

    /**
     * Sends a command with given payload and metadata and returns a future that will be completed with the command's
     * result.
     */
    static <R> R sendCommandAndWait(Object payload, Metadata metadata) {
        return get().commandGateway().sendAndWait(payload, metadata);
    }

    /**
     * Sends the given query and returns a future that will be completed with the query's result. The query may be an
     * instance of a {@link Message} in which case it will be sent as is. Otherwise the query is published using the
     * passed value as payload without additional metadata.
     */
    static <R> CompletableFuture<R> query(Object query) {
        return get().queryGateway().send(query);
    }

    /**
     * Sends a query with given payload and metadata and returns a future that will be completed with the query's
     * result.
     */
    static <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        return get().queryGateway().send(payload, metadata);
    }

    /**
     * Sends the given query and returns the query's result. The query may be an instance of a {@link Message} in which
     * case it will be sent as is. Otherwise the query is published using the passed value as payload without additional
     * metadata.
     */
    static <R> R queryAndWait(Object query) {
        return get().queryGateway().sendAndWait(query);
    }

    /**
     * Sends a query with given payload and metadata and returns the query's result.
     */
    static <R> R queryAndWait(Object payload, Metadata metadata) {
        return get().queryGateway().sendAndWait(payload, metadata);
    }

    /**
     * Schedules a message for the given timestamp, returning the schedule's id. The {@code schedule} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static String schedule(Object schedule, Instant deadline) {
        return get().scheduler().schedule(schedule, deadline);
    }

    /**
     * Schedules a message with given {@code scheduleId} for the given timestamp. The {@code schedule} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static void schedule(Object schedule, String scheduleId, Instant deadline) {
        get().scheduler().schedule(schedule, scheduleId, deadline);
    }

    /**
     * Schedules a command for the given timestamp, returning the command schedule's id. The {@code command} parameter
     * may be an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is
     * scheduled using the passed value as payload without additional metadata.
     */
    static String scheduleCommand(Object command, Instant deadline) {
        return get().scheduler().scheduleCommand(command, deadline);
    }

    /**
     * Schedules a command with given {@code scheduleId} for the given timestamp. The {@code command} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is published
     * using the passed value as payload without additional metadata.
     */
    static void scheduleCommand(Object command, String scheduleId, Instant deadline) {
        get().scheduler().scheduleCommand(command, scheduleId, deadline);
    }

    /**
     * Cancels the schedule with given {@code scheduleId}.
     */
    static void cancelSchedule(String scheduleId) {
        get().scheduler().cancelSchedule(scheduleId);
    }

    /**
     * Publishes a metrics event. The parameter may be an instance of a {@link Message} in which case it will be sent as
     * is. Otherwise the metrics event is published using the passed value as payload without additional metadata.
     * <p>
     * Metrics events can be published in any form to log custom performance metrics about an application.
     */
    static void publishMetrics(Object metrics) {
        get().metricsGateway().publish(metrics);
    }

    /**
     * Publishes a metrics event with given payload and metadata. Metrics events can be published in any form to log
     * custom performance metrics about an application.
     */
    static void publishMetrics(Object payload, Metadata metadata) {
        get().metricsGateway().publish(payload, metadata, Guarantee.NONE);
    }

    /**
     * Loads the aggregate root of type {@code <T>} with given aggregateId.
     * <p>
     * If the aggregate is loaded while handling an event of the aggregate, the returned Aggregate will automatically be
     * replayed back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> AggregateRoot<T> loadAggregate(String aggregateId, Class<T> aggregateType) {
        AggregateRoot<T> result = get().aggregateRepository().load(aggregateId, aggregateType);
        DeserializingMessage message = DeserializingMessage.getCurrent();
        if (message != null && (message.getMessageType() == EVENT || message.getMessageType() == NOTIFICATION)
            && aggregateId.equals(getAggregateId(message))) {
            return result.playBackToEvent(message.getMessageId());
        }
        return result;
    }

    /**
     * Loads the aggregate root of type {@code <T>} that currently contains the entity with given entityId. If no such
     * aggregate exists an empty aggregate root is returned with given {@code defaultType} as its type.
     * <p>
     * This method can also be used if the entity is the aggregate root (aggregateId is equal to entityId). If the
     * entity is associated with more than one aggregate the behavior of this method is unpredictable, though the
     * default behavior is that any one of the associated aggregates is returned.
     * <p>
     * If the aggregate is loaded while handling an event of the aggregate, the returned Aggregate will automatically be
     * replayed back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> AggregateRoot<T> loadAggregateFor(Object entityId, Class<?> defaultType) {
        AggregateRoot<T> result = get().aggregateRepository().loadFor(entityId.toString(), defaultType);
        DeserializingMessage message = DeserializingMessage.getCurrent();
        if (message != null && (message.getMessageType() == EVENT || message.getMessageType() == NOTIFICATION)
            && entityId.equals(getAggregateId(message))) {
            return result.playBackToEvent(message.getMessageId());
        }
        return result;
    }

    /**
     * Loads the aggregate root that currently contains the entity with given entityId. If no such aggregate exists an
     * empty aggregate root is returned of type {@code Object}. In that case be aware that applying events to create the
     * aggregate may yield an undesired result; to prevent this use {@link #loadAggregateFor(Object, Class)}.
     * <p>
     * This method can also be used if the entity is the aggregate root (aggregateId is equal to entityId). If the
     * entity is associated with more than one aggregate the behavior of this method is unpredictable, though the
     * default behavior is that any one of the associated aggregates is returned.
     * <p>
     * If the aggregate is loaded while handling an event of the aggregate, the returned Aggregate will automatically be
     * replayed back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> AggregateRoot<T> loadAggregateFor(Object entityId) {
        return loadAggregateFor(entityId, Object.class);
    }

    /**
     * Loads the entity with given id. If the entity is not associated with any aggregate yet, a new aggregate root is
     * loaded with the entityId as aggregate identifier. no such aggregate exists an empty aggregate root is returned of
     * type {@code Object}. In case multiple entities are associated with the given entityId the most recent entity is
     * returned.
     */
    @SuppressWarnings("unchecked")
    static <T> Entity<?, T> loadEntity(Object entityId) {
        return (Entity<?, T>) loadAggregateFor(entityId).getEntity(entityId)
                .orElseGet(() -> loadAggregate(entityId.toString(), Object.class));
    }

    /**
     * Loads the current entity value for given entity id. Entity may be the aggregate root or any ancestral entity. If
     * no such entity exists an empty optional is returned.
     */
    @SuppressWarnings("unchecked")
    static <T> Optional<T> loadEntityValue(String entityId) {
        return loadAggregateFor(entityId).getEntity(entityId).map(e -> (T) e.get());
    }

    /**
     * Convenience method to apply the given events to the aggregate with id {@code aggregateId} without loading the
     * aggregate. Events may be {@link Message} instances or event message payloads.
     */
    static void applyEvents(String aggregateId, Object... events) {
        get().aggregateRepository().applyEvents(aggregateId, events);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static void index(Object object, String id, String collection) {
        get().documentStore().index(object, id, collection);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static void index(Object object, String id, String collection, @Nullable Instant timestamp) {
        get().documentStore().index(object, id, collection, timestamp);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static void index(Object object, String id, String collection, @Nullable Instant timestamp, @Nullable Instant end) {
        get().documentStore().index(object, id, collection, timestamp, end);
    }

    /**
     * Index given objects for search. Use {@code idFunction} to provide the document's required id. Use
     * {@code timestampFunction} and {@code endFunction} to provide the object's timestamp. If none are supplied the
     * document will not be timestamped.
     * <p>
     * This method returns once all objects are stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction,
                          Function<? super T, Instant> timestampFunction, Function<? super T, Instant> endFunction) {
        get().documentStore().index(objects, collection, idFunction, timestampFunction, endFunction);
    }

    /**
     * Search the given collections for documents.
     * <p>
     * Example usage: FluxCapacitor.search("myCollection", "myOtherCollection).query("foo !bar").fetch(100);
     */
    static Search search(String... collections) {
        return get().documentStore().search(collections);
    }

    /**
     * Search the given collection for documents.
     * <p>
     * Example usage: FluxCapacitor.search("myCollection").query("foo !bar").fetch(100);
     */
    static Search search(String collection) {
        return get().documentStore().search(collection);
    }

    /**
     * Registers given handlers and initiates message tracking (i.e. listening for messages).
     * <p>
     * The given handlers will be inspected for annotated handler methods (e.g. methods annotated with
     * {@link HandleCommand}). Depending on this inspection message tracking will commence for any handled message
     * types. To stop listening at any time invoke {@link Registration#cancel()} on the returned object.
     * <p>
     * Note that an exception may be thrown if tracking for a given message type is already in progress.
     * <p>
     * If any of the handlers is a local handler or contains local handler methods, i.e. if type or method is annotated
     * with {@link LocalHandler}, the target object will (also) be registered as local handler. Local handlers will
     * handle messages in the publishing thread. If a published message can be handled locally it will not be published
     * to the Flux Capacitor service. Local handling of messages may come in handy in several situations: e.g. when the
     * message is expressly meant to be handled only by the current application or if the message needs to be handled as
     * quickly as possible. However, in most cases it will not be necessary to register local handlers.
     * <p>
     * Note that it will generally not be necessary to invoke this method manually if you use Spring to configure your
     * application.
     *
     * @see FluxCapacitorSpringConfig for more info on how to configure your application using Spring
     * @see LocalHandler for more info on local handlers.
     */
    default Registration registerHandlers(Object... handlers) {
        return registerHandlers(Arrays.asList(handlers));
    }

    /**
     * Registers given handlers and initiates message tracking.
     *
     * @see #registerHandlers(Object...) for more info
     */
    default Registration registerHandlers(List<?> handlers) {
        return apply(f -> {
            Registration local = handlers.stream().flatMap(h -> Stream
                            .of(commandGateway().registerHandler(h), queryGateway().registerHandler(h),
                                eventGateway().registerHandler(h), eventStore().registerHandler(h),
                                errorGateway().registerHandler(h), webRequestGateway().registerHandler(h)))
                    .reduce(Registration::merge).orElse(Registration.noOp());
            Registration tracking = stream(MessageType.values()).map(t -> tracking(t).start(this, handlers))
                    .reduce(Registration::merge).orElse(Registration.noOp());
            return tracking.merge(local);
        });
    }

    /**
     * Have Flux Capacitor use the given Clock when generating timestamps, e.g. when creating a {@link Message}.
     */
    void withClock(Clock clock);

    /**
     * Returns a client to assist with event sourcing.
     */
    AggregateRepository aggregateRepository();

    /**
     * Returns the event store client.
     */
    EventStore eventStore();

    /**
     * Returns the message scheduling client.
     */
    Scheduler scheduler();

    /**
     * Returns the gateway for command messages.
     */
    CommandGateway commandGateway();

    /**
     * Returns the gateway for query messages.
     */
    QueryGateway queryGateway();

    /**
     * Returns the message gateway for application events. Use {@link #aggregateRepository()} to publish events
     * belonging to an aggregate.
     */
    EventGateway eventGateway();

    /**
     * Returns the gateway for result messages sent by handlers of commands and queries.
     */
    ResultGateway resultGateway();

    /**
     * Returns the gateway for any error messages published while handling a command or query.
     */
    ErrorGateway errorGateway();

    /**
     * Returns the gateway for metrics events. Metrics events can be published in any form to log custom performance
     * metrics about an application.
     */
    MetricsGateway metricsGateway();

    /**
     * Returns the gateway for sending web requests.
     */
    WebRequestGateway webRequestGateway();

    /**
     * Returns a client to assist with the tracking of a given message type.
     */
    Tracking tracking(MessageType messageType);

    /**
     * Returns a client for the key value service offered by Flux Capacitor.
     */
    KeyValueStore keyValueStore();

    /**
     * Returns a client for the document search service offered by Flux Capacitor.
     */
    DocumentStore documentStore();

    /**
     * Returns the UserProvider used by Flux Capacitor to authenticate users. May be {@code null} if user authentication
     * is disabled.
     */
    UserProvider userProvider();

    /**
     * Returns the cache used by the client to cache aggregates etc.
     */
    Cache cache();

    /**
     * Returns the provider of correlation data for published messages.
     */
    CorrelationDataProvider correlationDataProvider();

    /**
     * Returns the default serializer
     */
    Serializer serializer();

    /**
     * Returns the clock used by Flux Capacitor to generate timestamps.
     */
    Clock clock();

    /**
     * Returns the factory used by Flux Capacitor to generate identifiers.
     */
    IdentityProvider identityProvider();

    /**
     * Returns the low level client used by this FluxCapacitor instance to interface with the Flux Capacitor service. Of
     * course the returned client may also be a stand-in for the actual service.
     */
    Client client();

    /**
     * Applies the given function with this Flux Capacitor set as current threadlocal instance.
     */
    default <R> R apply(Function<FluxCapacitor, R> function) {
        FluxCapacitor current = FluxCapacitor.instance.get();
        try {
            FluxCapacitor.instance.set(this);
            return function.apply(this);
        } finally {
            FluxCapacitor.instance.set(current);
        }
    }

    /**
     * Executes the given task with this Flux Capacitor set as current threadlocal instance.
     */
    default void execute(Consumer<FluxCapacitor> task) {
        FluxCapacitor current = FluxCapacitor.instance.get();
        try {
            FluxCapacitor.instance.set(this);
            task.accept(this);
        } finally {
            FluxCapacitor.instance.set(current);
        }
    }

    /**
     * Register a task to run before this Flux Capacitor instance is closed.
     */
    Registration beforeShutdown(Runnable task);

    @Override
    void close();
}
