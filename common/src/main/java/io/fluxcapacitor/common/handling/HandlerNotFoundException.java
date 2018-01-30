package io.fluxcapacitor.common.handling;

public class HandlerNotFoundException extends RuntimeException {

    public HandlerNotFoundException(String message) {
        super(message);
    }

}
