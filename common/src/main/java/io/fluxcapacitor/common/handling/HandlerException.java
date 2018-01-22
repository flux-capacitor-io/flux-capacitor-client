package io.fluxcapacitor.common.handling;

public class HandlerException extends RuntimeException {

    public HandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public synchronized Exception getCause() {
        return (Exception) super.getCause();
    }
}
