package io.fluxcapacitor.javaclient.configuration.client;

import lombok.Getter;

@Getter
public class WebSocketClientProperties extends ClientProperties {
    private final String fluxCapacitorUrl;

    public WebSocketClientProperties(String applicationName, String clientId, String fluxCapacitorUrl) {
        super(applicationName, clientId);
        this.fluxCapacitorUrl = fluxCapacitorUrl;
    }

    public WebSocketClientProperties(String applicationName, String fluxCapacitorUrl) {
        super(applicationName);
        this.fluxCapacitorUrl = fluxCapacitorUrl;
    }
}
