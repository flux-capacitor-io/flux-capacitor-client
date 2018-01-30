package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourcing;
import io.fluxcapacitor.javaclient.gateway.CommandGateway;
import io.fluxcapacitor.javaclient.gateway.EventGateway;
import io.fluxcapacitor.javaclient.gateway.QueryGateway;
import io.fluxcapacitor.javaclient.gateway.ResultGateway;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
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
 * To build an instance of this API check out {@link io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor}.
 */
public interface FluxCapacitor {

    ThreadLocal<FluxCapacitor> instance = new ThreadLocal<>();

    static FluxCapacitor get() {
        return Optional.ofNullable(instance.get())
                .orElseThrow(() -> new IllegalStateException("FluxCapacitor instance not set"));
    }

    static void publishEvent(Object payload) {
        get().eventGateway().publishEvent(payload);
    }

    static void publishEvent(Object payload, Metadata metadata) {
        get().eventGateway().publishEvent(payload, metadata);
    }

    static void sendAndForgetCommand(Object payload, Metadata metadata) {
        get().commandGateway().sendAndForget(payload, metadata);
    }

    static void sendAndForgetCommand(Object payload) {
        get().commandGateway().sendAndForget(payload, Metadata.empty());
    }

    static <R> CompletableFuture<R> sendCommand(Object payload, Metadata metadata) {
        return get().commandGateway().send(payload, metadata);
    }

    static <R> CompletableFuture<R> sendCommand(Object payload) {
        return get().commandGateway().send(payload, Metadata.empty());
    }

    static <R> CompletableFuture<R> query(Object payload) {
        return get().queryGateway().query(payload);
    }

    static <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        return get().queryGateway().query(payload, metadata);
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

    Tracking tracking(MessageType messageType);
}
