package io.fluxcapacitor.javaclient.configuration;

import java.beans.ConstructorProperties;

public class ConfigurationException extends RuntimeException {

    @ConstructorProperties({"message"})
    public ConfigurationException(String message) {
        super(message);
    }
}
