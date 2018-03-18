package io.fluxcapacitor.javaclient.publishing;

import java.beans.ConstructorProperties;

public class TimeoutException extends RuntimeException {
    @ConstructorProperties({"message", "cause"})
    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

