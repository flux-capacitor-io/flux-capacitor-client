package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

@FunctionalInterface
public interface EventSourcingHandler<T> {
    T apply(DeserializingMessage message, T model);
}
