package io.fluxcapacitor.javaclient.persisting.eventsourcing;

public interface EventSourcingHandlerFactory {
    <T> EventSourcingHandler<T> forType(Class<?> type);
}
