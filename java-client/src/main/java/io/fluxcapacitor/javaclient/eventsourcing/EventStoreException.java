package io.fluxcapacitor.javaclient.eventsourcing;

public class EventStoreException extends RuntimeException {
    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
