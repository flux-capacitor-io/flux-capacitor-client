package io.fluxcapacitor.javaclient.eventsourcing;

public class DuplicateModelException extends RuntimeException {
    public DuplicateModelException(String message) {
        super(message);
    }
}
