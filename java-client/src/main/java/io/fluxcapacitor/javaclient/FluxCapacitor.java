package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.model.Model;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourcing;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.*;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracking;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.stream;

/**
 * High-level client API for Flux Capacitor. If you are using anything other than this to interact with the service
 * at runtime you're probably doing it wrong.
 * <p>
 * To start handling messages build an instance of this API and invoke {@link #startTracking}.
 * <p>
 * Once you are handling messages you can simply use the static methods provided (e.g. to publish messages etc).
 * In those cases it is not necessary to inject an instance of this API. This minimizes the need for dependencies
 * in your functional classes and maximally cashes in on location transparency.
 * <p>
 * To build an instance of this API check out {@link DefaultFluxCapacitor}.
 */
public interface FluxCapacitor {

    ThreadLocal<FluxCapacitor> instance = new ThreadLocal<>();

    static FluxCapacitor get() {
        return Optional.ofNullable(instance.get())
                .orElseThrow(() -> new IllegalStateException("FluxCapacitor instance not set"));
    }

    static void publishEvent(Object event) {
        get().eventGateway().publish(event);
    }

    static void publishEvent(Object payload, Metadata metadata) {
        get().eventGateway().publish(payload, metadata);
    }

    static void sendAndForgetCommand(Object payload, Metadata metadata) {
        get().commandGateway().sendAndForget(payload, metadata);
    }

    static void sendAndForgetCommand(Object command) {
        get().commandGateway().sendAndForget(command, Metadata.empty());
    }

    static <R> CompletableFuture<R> sendCommand(Object payload, Metadata metadata) {
        return get().commandGateway().send(payload, metadata);
    }

    static <R> CompletableFuture<R> sendCommand(Object command) {
        return get().commandGateway().send(command, Metadata.empty());
    }

    static <R> CompletableFuture<R> query(Object query) {
        return get().queryGateway().query(query);
    }

    static <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        return get().queryGateway().query(payload, metadata);
    }

    static void publishMetrics(Object metrics) {
        get().metricsGateway().publish(metrics);
    }

    static void publishMetrics(Object payload, Metadata metadata) {
        get().metricsGateway().publish(payload, metadata);
    }

    static <T> Model<T> loadAggregate(String id, Class<T> modelType) {
        if (modelType.isAnnotationPresent(EventSourced.class)) {
            return get().eventSourcing().load(id, modelType);
        }
        throw new UnsupportedOperationException("Only event sourced aggregates are supported at the moment");
    }

    @SuppressWarnings("ConstantConditions")
    default Registration startTracking(Object... handlers) {
        return stream(MessageType.values())
                .map(t -> tracking(t).start(this, handlers)).reduce(Registration::merge).get();
    }

    EventSourcing eventSourcing();

    Scheduler scheduler();

    KeyValueStore keyValueStore();

    CommandGateway commandGateway();

    QueryGateway queryGateway();

    EventGateway eventGateway();

    ResultGateway resultGateway();

    ErrorGateway errorGateway();

    MetricsGateway metricsGateway();

    Tracking tracking(MessageType messageType);

    Client client();
}
