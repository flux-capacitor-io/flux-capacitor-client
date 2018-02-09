package io.fluxcapacitor.common.handling;

import java.beans.ConstructorProperties;

public class HandlerException extends RuntimeException {
    public HandlerException(String message) {
        super(message);
    }

    @ConstructorProperties({"message", "cause"})
    public HandlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
