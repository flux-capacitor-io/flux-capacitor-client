package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

public interface EventSourcingHandler<T> {

    T invoke(T target, DeserializingMessage message);
    
    boolean canHandle(T model, DeserializingMessage message);
}
