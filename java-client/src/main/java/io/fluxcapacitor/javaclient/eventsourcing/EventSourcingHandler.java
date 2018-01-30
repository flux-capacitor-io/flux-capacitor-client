package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

@FunctionalInterface
public interface EventSourcingHandler<T> {
    T apply(Message message, T model);
}
