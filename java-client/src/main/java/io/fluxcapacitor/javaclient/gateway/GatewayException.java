package io.fluxcapacitor.javaclient.gateway;

public class GatewayException extends RuntimeException {
    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
