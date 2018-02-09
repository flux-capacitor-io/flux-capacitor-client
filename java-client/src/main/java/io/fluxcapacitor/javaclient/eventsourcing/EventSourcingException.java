package io.fluxcapacitor.javaclient.eventsourcing;

import java.beans.ConstructorProperties;

public class EventSourcingException extends RuntimeException {

    public EventSourcingException(String message) {
        super(message);
    }

    @ConstructorProperties({"message", "cause"})
    public EventSourcingException(String message, Throwable cause) {
        super(message, cause);
    }
}
