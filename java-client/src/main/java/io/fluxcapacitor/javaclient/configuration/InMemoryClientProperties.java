package io.fluxcapacitor.javaclient.configuration;

public class InMemoryClientProperties extends ClientProperties {
    public InMemoryClientProperties(String applicationName, String clientId) {
        super(applicationName, clientId);
    }

    public InMemoryClientProperties(String applicationName) {
        super(applicationName);
    }
}
