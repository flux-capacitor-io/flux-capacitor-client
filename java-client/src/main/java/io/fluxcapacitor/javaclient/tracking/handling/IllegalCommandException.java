package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

public class IllegalCommandException extends FunctionalException {
    public IllegalCommandException(String message, Object command) {
        super(String.format("Illegal command: %s : %s", message, command));
    }

    public IllegalCommandException(String message) {
        super(message);
    }
}
