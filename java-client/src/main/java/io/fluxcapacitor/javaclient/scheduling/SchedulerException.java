package io.fluxcapacitor.javaclient.scheduling;

import java.beans.ConstructorProperties;

public class SchedulerException extends RuntimeException {
    @ConstructorProperties({"message", "cause"})
    public SchedulerException(String message, Throwable cause) {
        super(message, cause);
    }
}
