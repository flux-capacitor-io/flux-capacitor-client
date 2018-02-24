package io.fluxcapacitor.javaclient.eventsourcing;

public class ModelNotFoundException extends RuntimeException {
    public ModelNotFoundException(String message) {
        super(message);
    }
}
