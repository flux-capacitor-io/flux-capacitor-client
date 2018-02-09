package io.fluxcapacitor.javaclient.publishing;

import java.beans.ConstructorProperties;

public class GatewayException extends RuntimeException {
    @ConstructorProperties({"message", "cause"})
    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

