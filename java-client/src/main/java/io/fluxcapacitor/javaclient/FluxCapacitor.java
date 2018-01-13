package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.gateway.Gateway;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracker;

import java.util.Optional;

public interface FluxCapacitor {

    ThreadLocal<FluxCapacitor> instance = new ThreadLocal<>();

    static FluxCapacitor get() {
        return Optional.ofNullable(instance.get())
                .orElseThrow(() -> new IllegalStateException("FluxCapacitor instance not set"));
    }

    static Awaitable publishEvent(Object payload) {
        return get().gateway(MessageType.EVENT).publish(payload);
    }

    static Awaitable publishEvent(Object payload, Metadata metadata) {
        return get().gateway(MessageType.EVENT).publish(payload, metadata);
    }

    static Awaitable dispatchCommand(Object payload) {
        return get().gateway(MessageType.COMMAND).publish(payload);
    }

    static Awaitable dispatchCommand(Object payload, Metadata metadata) {
        return get().gateway(MessageType.COMMAND).publish(payload, metadata);
    }

    static Awaitable query(Object payload) {
        return get().gateway(MessageType.QUERY).publish(payload);
    }

    static Awaitable query(Object payload, Metadata metadata) {
        return get().gateway(MessageType.QUERY).publish(payload, metadata);
    }

    EventStore eventStore();

    Scheduler scheduler();

    KeyValueStore keyValueStore();

    Gateway gateway(MessageType messageType);

    Registration track(MessageType messageType, Tracker tracker);
}
