package io.fluxcapacitor.common.handling;

import java.beans.ConstructorProperties;

public class HandlerNotFoundException extends RuntimeException {

    @ConstructorProperties({"message"})
    public HandlerNotFoundException(String message) {
        super(message);
    }

}
