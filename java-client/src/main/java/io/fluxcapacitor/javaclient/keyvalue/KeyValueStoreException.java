package io.fluxcapacitor.javaclient.keyvalue;

import java.beans.ConstructorProperties;

public class KeyValueStoreException extends RuntimeException {
    @ConstructorProperties({"message", "cause"})
    public KeyValueStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
