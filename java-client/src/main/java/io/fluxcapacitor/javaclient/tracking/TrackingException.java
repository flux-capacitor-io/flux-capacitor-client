package io.fluxcapacitor.javaclient.tracking;

import java.beans.ConstructorProperties;

public class TrackingException extends RuntimeException {
    @ConstructorProperties({"message"})
    public TrackingException(String message) {
        super(message);
    }
}
