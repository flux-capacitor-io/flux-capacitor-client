package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.model.Model;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.configuration.spring.LocalHandler;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourcing;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

/**
 * High-level client for Flux Capacitor. If you are using anything other than this to interact with the service at
 * runtime you're probably doing it wrong.
 * <p>
 * To start handling messages build an instance of this API and invoke {@link #startTracking}.
 * <p>
 * Once you are handling messages you can simply use the static methods provided (e.g. to publish messages etc). In
 * those cases it is not necessary to inject an instance of this API. This minimizes the need for dependencies in your
 * functional classes and maximally cashes in on location transparency.
 * <p>
 * To build an instance of this client check out {@link DefaultFluxCapacitor}.
 */
public interface FluxCapacitor {

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
     * Returns the FluxCapacitor client bound to the current thread or else set by the current application. 
     * Throws an exception if no client was registered.
     */
    static FluxCapacitor get() {
        return Optional.ofNullable(instance.get())
                .orElseGet(() -> Optional.ofNullable(applicationInstance.get())
                                .orElseThrow(() -> new IllegalStateException("FluxCapacitor instance not set")));
    }

    /**
     * Publishes the given application event. The event may be an instance of a {@link Message} in which case it will be
     * published as is. Otherwise the event is published using the passed value as payload without additional metadata.
     * <p>
     * Note that the published event will not be available for event sourcing as it is does not belong to any
     * aggregate.
     *
     * @see #eventSourcing() if you're interested in publishing events that belong to an aggregate.
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
     * Sends the given command and don't wait for a result. The command may be an instance of a {@link Message} in which
     * case it will be sent as is. Otherwise the command is published using the passed value as payload without
     * additional metadata.
     *
     * @see #sendCommand(Object) to send a command and inspect its result
     */
    static void sendAndForgetCommand(Object command) {
        get().commandGateway().sendAndForget(command);
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
     * Sends the given command and returns a future that will be completed with the command's result. The command may be
     * an instance of a {@link Message} in which case it will be sent as is. Otherwise the command is published using
     * the passed value as payload without additional metadata.
     */
    static <R> CompletableFuture<R> sendCommand(Object command) {
        return get().commandGateway().send(command);
    }

    /**
     * Sends a command with given payload and metadata and returns a future that will be completed with the command's
     * result.
     */
    static <R> CompletableFuture<R> sendCommand(Object payload, Metadata metadata) {
        return get().commandGateway().send(payload, metadata);
    }

    /**
     * Sends the given command and returns the command's result. The command 
     * may be an instance of a {@link Message} in which case it will be sent as is. Otherwise the command is published 
     * using the passed value as payload without additional metadata.
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
     * Sends the given query and returns the query's result. The query may be an
     * instance of a {@link Message} in which case it will be sent as is. Otherwise the query is published using the
     * passed value as payload without additional metadata.
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
        get().metricsGateway().publish(payload, metadata);
    }

    /**
     * Loads the most recent aggregate root of type {@link T} with given id.
     *
     * @see EventSourced for more info on how to define an event sourced aggregate root
     */
    static <T> Model<T> loadAggregate(String id, Class<T> modelType) {
        if (modelType.isAnnotationPresent(EventSourced.class)) {
            return get().eventSourcing().load(id, modelType);
        }
        throw new UnsupportedOperationException("Only event sourced aggregates are supported at the moment");
    }

    /**
     * Returns the property value registered with the given key. If the property can't be found on the FluxCapacitor
     * instance it will try to get the property using {@link System#getProperty(String)}
     */
    static String getProperty(String key) {
        return get().properties().getProperty(key, System.getProperty(key));
    }

    /**
     * Returns the property value registered with the given key. If the property can't be found on the FluxCapacitor
     * instance it will try to get the property using {@link System#getProperty(String)}. If the property is also not
     * known as system property the given default value will be returned.
     */
    static String getProperty(String key, String defaultValue) {
        return get().properties().getProperty(key, System.getProperty(key, defaultValue));
    }

    /**
     * Registers given handlers and initiates message tracking (i.e. listening for messages). The given handlers will be
     * inspected for annotated handler methods (e.g. methods annotated with {@link HandleCommand}). Depending on this
     * inspection message tracking will commence for any handled message types. To stop tracking at any time invoke
     * {@link Registration#cancel()} on the returned object.
     * <p>
     * Note that an exception may be thrown if tracking for a given message type is already in progress.
     * <p>
     * Note also that it will generally not be necessary to ever invoke this method manually if you have you use Spring
     * to configure your application.
     *
     * @see FluxCapacitorSpringConfig for more info on how to configure your application using Spring
     */
    default Registration startTracking(Object... handlers) {
        return startTracking(Arrays.asList(handlers));
    }

    /**
     * Registers given handlers and initiates message tracking.
     *
     * @see #startTracking(Object...) for more info
     */
    @SuppressWarnings("ConstantConditions")
    default Registration startTracking(List<?> handlers) {
        return stream(MessageType.values())
                .map(t -> tracking(t).start(this, handlers)).reduce(Registration::merge).orElse(Registration.noOp());
    }

    /**
     * Registers given message handlers for immediate handling of messages as they are published by the application. The
     * given handlers will be inspected for annotated handler methods (e.g. methods annotated with {@link
     * HandleCommand}).
     * <p>
     * If a published message can be handled locally it will not be published to the Flux Capacitor service. Local
     * handling of messages may come in handy in several situations: e.g. when the message is expressly meant to be
     * handled only by the current application or if the message needs to be handled as quickly as possible. However, in
     * most cases it will not be necessary to register any local handlers.
     * <p>
     * To stop listening for locally published messages invoke {@link Registration#cancel()} on the returned object.
     *
     * @see LocalHandler for more info on how to automatically register local handlers using Spring.
     */
    default Registration registerLocalHandlers(Object... handlers) {
        return registerLocalHandlers(Arrays.asList(handlers));
    }

    /**
     * Registers given message handlers for immediate handling of messages as they are published by the application.
     *
     * @see #registerLocalHandlers(Object...) for more info
     */
    default Registration registerLocalHandlers(List<?> handlers) {
        return handlers.stream().flatMap(h -> Stream
                .of(commandGateway().registerLocalHandler(h), queryGateway().registerLocalHandler(h),
                    eventGateway().registerLocalHandler(h), 
                    eventSourcing().eventStore().registerLocalHandler(h)))
                .reduce(Registration::merge).orElse(Registration.noOp());
    }

    /**
     * Returns a client to assist with event sourcing.
     */
    EventSourcing eventSourcing();

    /**
     * Returns the message scheduling client.
     */
    Scheduler scheduler();

    /**
     * Returns a client for the key value service offered by Flux Capacitor.
     */
    KeyValueStore keyValueStore();

    /**
     * Returns the gateway for command messages.
     */
    CommandGateway commandGateway();

    /**
     * Returns the gateway for query messages.
     */
    QueryGateway queryGateway();

    /**
     * Returns the message gateway for application events. Use {@link #eventSourcing()} to publish events belonging to
     * an aggregate.
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
     * Returns the gateway for metrics events. Metrics events can be published in any form to log custom
     * performance metrics about an application.
     */
    MetricsGateway metricsGateway();

    /**
     * Returns a client to assist with the tracking of a given message type.
     */
    Tracking tracking(MessageType messageType);

    /**
     * Returns the low level client used by this FluxCapacitor instance to interface with the Flux Capacitor service.
     * Of course the returned client may also be a stand-in for the actual service.   
     */
    Client client();

    /**
     * Returns custom properties registered with this FluxCapacitor instance.
     */
    Properties properties();

}
