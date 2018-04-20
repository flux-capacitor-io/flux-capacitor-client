package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

public class IllegalCommandException extends FunctionalException {
    public IllegalCommandException(String message, Object command) {
        super(String.format("Illegal command: %s : %s", message, command));
    }

    @SuppressWarnings("unused") //allows Jackson to deserialize this exception
    private IllegalCommandException(String message) {
        super(message);
    }
}
