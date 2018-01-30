package io.fluxcapacitor.common.handling;

public class HandlerException extends RuntimeException {

    public HandlerException(String message) {
        super(message);
    }

    public HandlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
