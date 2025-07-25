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

package io.fluxcapacitor.javaclient;

import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.application.PropertySource;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.UuidFactory;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.FilterContent;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorConfiguration;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.persisting.search.IndexOperation;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.GenericGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.WebRequestGateway;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import io.fluxcapacitor.javaclient.scheduling.MessageScheduler;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.CUSTOM;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static java.util.Arrays.stream;

/**
 * High-level entry point for all interactions with the Flux Capacitor platform.
 * <p>
 * This interface exposes static convenience methods to publish and track messages, interact with aggregates, schedule
 * tasks, index/search documents, and more. It is designed to reduce boilerplate and promote location transparency in
 * message-driven systems.
 * </p>
 *
 * <h2>Usage Patterns</h2>
 * <ul>
 *   <li>To send or publish messages, use static methods such as {@link #sendCommand(Object)} or {@link #publishEvent(Object)}.</li>
 *   <li>To track incoming messages, register handlers using {@link #registerHandlers(Object...)}.</li>
 *   <li>To interact with aggregates, use {@link #loadAggregate(Id)} or {@link #aggregateRepository()}.</li>
 * </ul>
 *
 * <p>
 * Most applications will never need to hold or inject a {@code FluxCapacitor} instance directly.
 * Instead, the platform automatically binds the relevant instance to a thread-local scope, allowing access via static methods.
 * </p>
 *
 * <p>
 * A concrete instance is typically constructed using {@link DefaultFluxCapacitor}.
 * </p>
 */
public interface FluxCapacitor extends AutoCloseable {

    /**
     * Flux Capacitor instance set by the current application. Used as a fallback when no threadlocal instance was set.
     * This is added as a convenience for applications that never have more than one than FluxCapacitor instance which
     * will be the case for nearly all applications. On application startup simply fill this application instance.
     */
    AtomicReference<FluxCapacitor> applicationInstance = new AtomicReference<>();

    /**
     * Thread-local binding of the current {@code FluxCapacitor} instance.
     * <p>
     * This is automatically set during message processing to ensure that handlers can invoke commands, queries, or
     * schedule events without explicitly injecting dependencies.
     * </p>
     *
     * <p>
     * Example: Inside a {@code @HandleCommand} method, you can call {@code FluxCapacitor.sendCommand(...)} and it will
     * automatically use the correct instance, without needing manual wiring.
     * </p>
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
     * Gets the time according to the current FluxCapacitor clock (obtained via {@link #currentClock()}). If there is no
     * current FluxCapacitor instance the system's UTC time is returned.
     */
    static Instant currentTime() {
        return currentClock().instant();
    }

    /**
     * Generates a functional ID using the current {@link IdentityProvider}. This is typically used for
     * application-level entities such as aggregates or user-defined messages.
     *
     * @return a unique, traceable identifier string
     */
    static String generateId() {
        return currentIdentityProvider().nextFunctionalId();
    }

    /**
     * Generates a strongly typed ID of given {@code idClass} using the current {@link IdentityProvider}.
     *
     * @return a unique, traceable typed identifier
     */
    static <T extends Id<?>> T generateId(Class<T> idClass) {
        return JsonUtils.convertValue(TextNode.valueOf(generateId()), idClass);
    }

    /**
     * Fetches the configured identity provider used for both functional and technical IDs. The default is a
     * {@link UuidFactory} that generates UUIDs.
     * <p>
     * If there is no current FluxCapacitor instance, a new UUID factory is generated.
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
     *
     * <p><strong>Note:</strong> These events are <em>not</em> persisted for event sourcing. To publish domain events
     * as part of an aggregate lifecycle, apply the events using {@link Entity#apply} after loading an entity.</p>
     *
     * @see #aggregateRepository() if you're interested in publishing events that belong to an aggregate.
     */
    static void publishEvent(Object event) {
        get().eventGateway().publish(event);
    }

    /**
     * Publishes an event with given payload and metadata.
     *
     * <p><strong>Note:</strong> These events are <em>not</em> persisted for event sourcing. To publish domain events
     * as part of an aggregate lifecycle, apply the events using {@link Entity#apply} after loading an entity.</p>
     *
     * @see #publishEvent(Object) for more info
     */
    static void publishEvent(Object payload, Metadata metadata) {
        get().eventGateway().publish(payload, metadata);
    }

    /**
     * Publishes given application events. The events may be instances of {@link Message} in which case they will be
     * published as is. Otherwise, the events are published using the passed value as payload without additional
     * metadata.
     * <p><strong>Note:</strong> These events are <em>not</em> persisted for event sourcing. To publish domain events
     * as part of an aggregate lifecycle, apply the events using {@link Entity#apply} after loading an entity.</p>
     *
     * @see #aggregateRepository() if you're interested in publishing events that belong to an aggregate.
     */
    static void publishEvents(Object... events) {
        get().eventGateway().publish(events);
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
     * Sends the given command and returns a future that will be completed with the command's result. The command may be
     * an instance of a {@link Message} in which case it will be sent as is. Otherwise the command is published using
     * the passed value as payload without additional metadata.
     * <p>
     * The return type is determined by the given command.
     */
    static <R> CompletableFuture<R> sendCommand(Request<R> command) {
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
     * Sends a command with given payload and metadata and returns a future that will be completed with the command's
     * result.
     * <p>
     * The return type is determined by the given command.
     */
    static <R> CompletableFuture<R> sendCommand(Request<R> payload, Metadata metadata) {
        return get().commandGateway().send(payload, metadata);
    }

    /**
     * Sends the given command and returns the command's result. The command may be an instance of a {@link Message} in
     * which case it will be sent as is. Otherwise, the command is published using the passed value as payload without
     * additional metadata.
     */
    static <R> R sendCommandAndWait(Object command) {
        return get().commandGateway().sendAndWait(command);
    }

    /**
     * Sends the given command and returns the command's result. The command may be an instance of a {@link Message} in
     * which case it will be sent as is. Otherwise, the command is published using the passed value as payload without
     * additional metadata.
     * <p>
     * The return type is determined by the given command.
     */
    static <R> R sendCommandAndWait(Request<R> command) {
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
     * Sends a command with given payload and metadata and returns a future that will be completed with the command's
     * result.
     * <p>
     * The return type is determined by the given command.
     */
    static <R> R sendCommandAndWait(Request<R> payload, Metadata metadata) {
        return get().commandGateway().sendAndWait(payload, metadata);
    }

    /**
     * Sends the given query and returns a future that will be completed with the query's result. The query may be an
     * instance of a {@link Message} in which case it will be sent as is. Otherwise, the query is published using the
     * passed value as payload without additional metadata.
     */
    static <R> CompletableFuture<R> query(Object query) {
        return get().queryGateway().send(query);
    }

    /**
     * Sends the given query and returns a future that will be completed with the query's result. The query may be an
     * instance of a {@link Message} in which case it will be sent as is. Otherwise, the query is published using the
     * passed value as payload without additional metadata.
     * <p>
     * The return type is determined by the given query.
     */
    static <R> CompletableFuture<R> query(Request<R> query) {
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
     * Sends a query with given payload and metadata and returns a future that will be completed with the query's
     * result.
     * <p>
     * The return type is determined by the given query.
     */
    static <R> CompletableFuture<R> query(Request<R> payload, Metadata metadata) {
        return get().queryGateway().send(payload, metadata);
    }

    /**
     * Sends the given query and returns the query's result. The query may be an instance of a {@link Message} in which
     * case it will be sent as is. Otherwise, the query is published using the passed value as payload without
     * additional metadata.
     */
    static <R> R queryAndWait(Object query) {
        return get().queryGateway().sendAndWait(query);
    }

    /**
     * Sends the given query and returns the query's result. The query may be an instance of a {@link Message} in which
     * case it will be sent as is. Otherwise, the query is published using the passed value as payload without
     * additional metadata.
     * <p>
     * The return type is determined by the given query.
     */
    static <R> R queryAndWait(Request<R> query) {
        return get().queryGateway().sendAndWait(query);
    }

    /**
     * Sends a query with given payload and metadata and returns the query's result.
     */
    static <R> R queryAndWait(Object payload, Metadata metadata) {
        return get().queryGateway().sendAndWait(payload, metadata);
    }

    /**
     * Sends a query with given payload and metadata and returns the query's result.
     * <p>
     * The return type is determined by the given query.
     */
    static <R> R queryAndWait(Request<R> payload, Metadata metadata) {
        return get().queryGateway().sendAndWait(payload, metadata);
    }

    /**
     * Starts a new periodic schedule, returning the schedule's id. The {@code schedule} parameter may be an instance of
     * a {@link Message} or the schedule payload. If the payload is not annotated with
     * {@link io.fluxcapacitor.javaclient.scheduling.Periodic} an {@link IllegalArgumentException} is thrown.
     *
     * @see io.fluxcapacitor.javaclient.scheduling.Periodic
     */
    static String schedulePeriodic(Object schedule) {
        return get().messageScheduler().schedulePeriodic(schedule);
    }

    /**
     * Starts a new periodic schedule using given schedule id. The {@code schedule} parameter may be an instance of a
     * {@link Message} or the schedule payload. If the payload is not annotated with
     * {@link io.fluxcapacitor.javaclient.scheduling.Periodic} an {@link IllegalArgumentException} is thrown.
     *
     * @see io.fluxcapacitor.javaclient.scheduling.Periodic
     */
    static void schedulePeriodic(Object schedule, String scheduleId) {
        get().messageScheduler().schedulePeriodic(schedule, scheduleId);
    }

    /**
     * Schedules a message for the given timestamp, returning the schedule's id. The {@code schedule} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static String schedule(Object schedule, Instant deadline) {
        return get().messageScheduler().schedule(schedule, deadline);
    }

    /**
     * Schedules a message with given {@code scheduleId} for the given timestamp. The {@code schedule} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static void schedule(Object schedule, String scheduleId, Instant deadline) {
        get().messageScheduler().schedule(schedule, scheduleId, deadline);
    }

    /**
     * Schedules a message after the given delay, returning the schedule's id. The {@code schedule} parameter may be an
     * instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static String schedule(Object schedule, Duration delay) {
        return get().messageScheduler().schedule(schedule, delay);
    }

    /**
     * Schedules a message with given {@code scheduleId} after given delay. The {@code schedule} parameter may be an
     * instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the schedule is published
     * using the passed value as payload without additional metadata.
     */
    static void schedule(Object schedule, String scheduleId, Duration delay) {
        get().messageScheduler().schedule(schedule, scheduleId, delay);
    }

    /**
     * Schedule a message object (of type {@link Schedule}) for execution, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the message to schedule
     */
    static void schedule(Schedule schedule) {
        get().messageScheduler().schedule(schedule);
    }

    /**
     * Schedule a message object (of type {@link Schedule}) for execution, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the message to schedule
     */
    static void schedule(Schedule schedule, boolean ifAbsent) {
        get().messageScheduler().schedule(schedule, ifAbsent);
    }

    /**
     * Schedules a command for the given timestamp, returning the command schedule's id. The {@code command} parameter
     * may be an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is
     * scheduled using the passed value as payload without additional metadata.
     */
    static String scheduleCommand(Object command, Instant deadline) {
        return get().messageScheduler().scheduleCommand(command, deadline);
    }

    /**
     * Schedules a command with given {@code scheduleId} for the given timestamp. The {@code command} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is published
     * using the passed value as payload without additional metadata.
     */
    static void scheduleCommand(Object command, String scheduleId, Instant deadline) {
        get().messageScheduler().scheduleCommand(command, scheduleId, deadline);
    }

    /**
     * Schedules a command after given delay, returning the command schedule's id. The {@code command} parameter may be
     * an instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is scheduled
     * using the passed value as payload without additional metadata.
     */
    static String scheduleCommand(Object command, Duration delay) {
        return get().messageScheduler().scheduleCommand(command, delay);
    }

    /**
     * Schedules a command with given {@code scheduleId} after given delay. The {@code command} parameter may be an
     * instance of a {@link Message} in which case it will be scheduled as is. Otherwise, the command is published using
     * the passed value as payload without additional metadata.
     */
    static void scheduleCommand(Object command, String scheduleId, Duration delay) {
        get().messageScheduler().scheduleCommand(command, scheduleId, delay);
    }

    /**
     * Schedule a command using the given scheduling settings, using the {@link Guarantee#SENT} guarantee.
     */
    static void scheduleCommand(Schedule message) {
        get().messageScheduler().scheduleCommand(message);
    }

    /**
     * Schedule a command using the given scheduling settings if no other with same ID exists, using the
     * {@link Guarantee#SENT} guarantee.
     */
    static void scheduleCommand(Schedule message, boolean ifAbsent) {
        get().messageScheduler().scheduleCommand(message, ifAbsent);
    }

    /**
     * Cancels the schedule with given {@code scheduleId}.
     */
    static void cancelSchedule(String scheduleId) {
        get().messageScheduler().cancelSchedule(scheduleId);
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
     * played back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> Entity<T> loadAggregate(Id<T> aggregateId) {
        return playbackToHandledEvent(get().aggregateRepository().load(aggregateId));
    }

    /**
     * Loads the aggregate root of type {@code <T>} with given aggregateId.
     * <p>
     * If the aggregate is loaded while handling an event of the aggregate, the returned Aggregate will automatically be
     * played back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> Entity<T> loadAggregate(Object aggregateId, Class<T> aggregateType) {
        return playbackToHandledEvent(get().aggregateRepository().load(aggregateId, aggregateType));
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
     * played back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> Entity<T> loadAggregateFor(Object entityId, Class<?> defaultType) {
        return playbackToHandledEvent(get().aggregateRepository().loadFor(entityId, defaultType));
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
     * played back to the event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see Aggregate for more info on how to define an event sourced aggregate root
     */
    static <T> Entity<T> loadAggregateFor(Object entityId) {
        return loadAggregateFor(entityId, entityId instanceof Id<?> id ? id.getType() : Object.class);
    }

    /**
     * Loads the entity with given id. If the entity is not associated with any aggregate yet, a new aggregate root is
     * loaded with the entityId as aggregate identifier. In case multiple entities are associated with the given
     * entityId the most recent entity is returned.
     * <p>
     * If the entity is loaded while handling an event its aggregate, the returned entity will automatically be played
     * back to the event currently being handled. Otherwise, the most recent state of the entity is loaded.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> Entity<T> loadEntity(Object entityId) {
        return (Entity<T>) loadAggregateFor(entityId).getEntity(entityId)
                .orElseGet(() -> entityId instanceof Id id
                        ? loadAggregate(id) : loadAggregate(entityId.toString(), Object.class));
    }

    /**
     * Loads the entity with given id. If the entity is not associated with any aggregate yet, a new aggregate root is
     * loaded with the entityId as aggregate identifier. In case multiple entities are associated with the given
     * entityId the most recent entity is returned.
     * <p>
     * If the entity is loaded while handling an event its aggregate, the returned entity will automatically be played
     * back to the event currently being handled. Otherwise, the most recent state of the entity is loaded.
     */
    static <T> Entity<T> loadEntity(Id<T> entityId) {
        return loadAggregateFor(entityId).<T>getEntity(entityId).orElseGet(() -> loadAggregate(entityId));
    }

    /**
     * Loads the current entity value for given entity id. Entity may be the aggregate root or any ancestral entity. If
     * no such entity exists or its value is not set {@code null} is returned.
     * <p>
     * If the entity is loaded while handling an event its aggregate, the returned entity will automatically be played
     * back to the event currently being handled. Otherwise, the most recent state of the entity is loaded.
     */
    @SuppressWarnings("unchecked")
    static <T> T loadEntityValue(Object entityId) {
        return (T) loadAggregateFor(entityId).getEntity(entityId).map(Entity::get).orElse(null);
    }

    /**
     * Loads the current entity value for given entity id. Entity may be the aggregate root or any ancestral entity. If
     * no such entity exists or its value is not set {@code null} is returned.
     * <p>
     * If the entity is loaded while handling an event its aggregate, the returned entity will automatically be played
     * back to the event currently being handled. Otherwise, the most recent state of the entity is loaded.
     */
    @SuppressWarnings("unchecked")
    static <T> T loadEntityValue(Id<T> entityId) {
        return (T) loadAggregateFor(entityId).getEntity(entityId).map(Entity::get).orElse(null);
    }

    /**
     * Returns an Entity containing given value. The returned entity won't exhibit any side effects when they are
     * updated, i.e. they won't be synced to any repository or give rise to any events. Other than, that they are fully
     * functional.
     */
    static <T> Entity<T> asEntity(T value) {
        return get().aggregateRepository().asEntity(value);
    }

    private static <T> Entity<T> playbackToHandledEvent(Entity<T> entity) {
        DeserializingMessage message = DeserializingMessage.getCurrent();
        if (!Entity.isApplying()
            && message != null && (message.getMessageType() == EVENT || message.getMessageType() == NOTIFICATION)
            && entity.rootAnnotation().eventSourced()
            && entity.id().toString().equals(Entity.getAggregateId(message))
            && Entity.hasSequenceNumber(message)) {
            return entity.playBackToEvent(message.getIndex(), message.getMessageId());
        }
        return entity;
    }

    /**
     * Prepare given object for indexing for search. This returns a mutable builder that allows defining an id,
     * collection, etc.
     * <p>
     * If the object is annotated with {@link Searchable @Searchable} the collection name and any timestamp or end path
     * defined there will be used.
     * <p>
     * If the object has a property annotated with {@link EntityId}, it will be used as the id of the document.
     * Otherwise, a random id will be assigned to the document.
     * <p>
     * This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     * @see Searchable for ways to define collection name etc
     */
    static IndexOperation prepareIndex(@NonNull Object object) {
        return get().documentStore().prepareIndex(object);
    }

    /**
     * Index given object for search.
     * <p>
     * If the object is annotated with {@link Searchable @Searchable} the collection name and any timestamp or end path
     * defined there will be used.
     * <p>
     * If the object has a property annotated with {@link EntityId}, it will be used as the id of the document.
     * Otherwise, a random id will be assigned to the document.
     * <p>
     * This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     * @see Searchable for ways to define collection name etc
     */
    static CompletableFuture<Void> index(Object object) {
        return get().documentStore().index(object);
    }

    /**
     * Index given object for search.
     * <p>
     * If the object has a property annotated with {@link EntityId}, it will be used as the id of the document.
     * Otherwise, a random id will be assigned to the document.
     * <p>
     * This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static CompletableFuture<Void> index(Object object, Object collection) {
        return get().documentStore().index(object, collection);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static CompletableFuture<Void> index(Object object, Object id, Object collection) {
        return get().documentStore().index(object, id, collection);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static CompletableFuture<Void> index(Object object, Object id, Object collection, Instant timestamp) {
        return get().documentStore().index(object, id, collection, timestamp);
    }

    /**
     * Index given object for search. This method returns once the object is stored.
     *
     * @see DocumentStore for more advanced uses.
     */
    static CompletableFuture<Void> index(Object object, Object id, Object collection, Instant begin, Instant end) {
        return get().documentStore().index(object, id, collection, begin, end);
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
    static <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                             Function<? super T, String> idFunction,
                                             Function<? super T, Instant> timestampFunction,
                                             Function<? super T, Instant> endFunction) {
        return get().documentStore().index(objects, collection, idFunction, timestampFunction, endFunction);
    }

    /**
     * Search the given collection for documents. Usually collection is the String name of the collection. However, it
     * is also possible to call it with a {@link Collection} containing one or multiple collection names.
     * <p>
     * If collection is of type {@link Class} it is expected that the class is annotated with {@link Searchable}. It
     * will then use the collection configured there.
     * <p>
     * For all other inputs, the collection name will be obtained by calling {@link Object#toString()} on the input.
     * <p>
     * Example usage: FluxCapacitor.search("myCollection").query("foo !bar").fetch(100);
     */
    static Search search(Object collection) {
        return get().documentStore().search(collection);
    }

    /**
     * Search the given collections for documents.
     * <p>
     * If collection is of type {@link Class} it is expected that the class is annotated with * {@link Searchable}. It
     * will then use the collection configured there. For all other inputs, the collection name will be obtained by
     * calling {@link Object#toString()} on the input.
     * <p>
     * Example usage: FluxCapacitor.search("myCollection", "myOtherCollection).query("foo !bar").fetch(100);
     */
    static Search search(Object collection, Object... additionalCollections) {
        return get().documentStore()
                .search(Stream.concat(Stream.of(collection), stream(additionalCollections)).toList());
    }

    /**
     * Search documents using given reusable query builder.
     * <p>
     * Example usage: FluxCapacitor.search(SearchQuery.builder().search("myCollection").query("foo !bar")).fetch(100);
     */
    static Search search(SearchQuery.Builder queryBuilder) {
        return get().documentStore().search(queryBuilder);
    }

    /**
     * Gets the document with given id in given collection, returning the value in the type that it was stored.
     */
    static <T> Optional<T> getDocument(Object id, Object collection) {
        return get().documentStore().fetchDocument(id, collection);
    }

    /**
     * Gets the document with given id in given collection type, returning the value.
     */
    static <T> Optional<T> getDocument(Object id, Class<T> collection) {
        return get().documentStore().fetchDocument(id, collection, collection);
    }

    /**
     * Gets the document with given id in given collection, converting the matching document to a value with given
     * type.
     */
    static <T> Optional<T> getDocument(Object id, Object collection, Class<T> type) {
        return get().documentStore().fetchDocument(id, collection, type);
    }

    /**
     * Deletes the document with given id in given collection if it exists.
     */
    static CompletableFuture<Void> deleteDocument(Object id, Object collection) {
        return get().documentStore().deleteDocument(id, collection);
    }

    /**
     * Deletes a search collection if it exists.
     */
    static CompletableFuture<Void> deleteCollection(Object collection) {
        return get().documentStore().deleteCollection(collection);
    }

    /**
     * Modify given value before it's passed to the given viewer. See {@link FilterContent} for info on how to filter
     * the value.
     */
    static <T> T filterContent(T value, User user) {
        return get().serializer().filterContent(value, user);
    }

    /**
     * Downcasts the given object to a previous revision.
     *
     * @param object          the object to downcast
     * @param desiredRevision the target revision
     * @return a serialized form of the object downcasted to the given revision
     */
    static Object downcast(Object object, int desiredRevision) {
        return get().serializer().downcast(object, desiredRevision);
    }

    /**
     * Downcasts a {@link Data} object to the specified revision level.
     *
     * @param data            the serialized data
     * @param desiredRevision the target revision number
     * @return a serialized form of the object downcasted to the given revision
     */
    static Object downcast(Data<?> data, int desiredRevision) {
        return get().serializer().downcast(data, desiredRevision);
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
                                errorGateway().registerHandler(h), webRequestGateway().registerHandler(h),
                                ClientUtils.getTopics(CUSTOM, h).stream()
                                        .map(topic -> customGateway(topic).registerHandler(h))
                                        .reduce(Registration::merge).orElse(Registration.noOp())))
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
     * Returns the store for aggregate events.
     */
    EventStore eventStore();

    /**
     * Returns the store for aggregate snapshots.
     */
    SnapshotStore snapshotStore();

    /**
     * Returns the gateway to schedule messages.
     *
     * @see MessageType#SCHEDULE
     */
    MessageScheduler messageScheduler();

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
     * Returns the gateway for given custom message topic.
     */
    GenericGateway customGateway(String topic);

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
     * Returns the {@link PropertySource} configured for this FluxCapacitor instance.
     */
    PropertySource propertySource();

    /**
     * Returns the {@link TaskScheduler} of this FluxCapacitor instance.
     */
    TaskScheduler taskScheduler();

    /**
     * Returns the {@link FluxCapacitorConfiguration} of this FluxCapacitor instance.
     */
    FluxCapacitorConfiguration configuration();

    /**
     * Returns the low level client used by this FluxCapacitor instance to interface with the Flux Capacitor service. Of
     * course the returned client may also be a stand-in for the actual service.
     */
    Client client();

    /**
     * Applies the given function with this Flux Capacitor set as current threadlocal instance.
     */
    @SneakyThrows
    default <R> R apply(ThrowingFunction<FluxCapacitor, R> function) {
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
    @SneakyThrows
    default void execute(ThrowingConsumer<FluxCapacitor> task) {
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

    /**
     * Closes this Flux Capacitor instance gracefully.
     */
    @Override
    default void close() {
        close(false);
    }

    /**
     * Closes this Flux Capacitor instance gracefully. If silently is true, shutdown is done without logging.
     */
    void close(boolean silently);
}
