package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

public interface EventSourcingHandler<T> {
    T apply(DeserializingMessage message, T model);
    
    boolean canHandle(DeserializingMessage message, T model);
}
