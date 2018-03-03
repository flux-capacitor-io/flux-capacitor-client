package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.ClientAction;
import lombok.Value;

@Value
public class HandleMessageAction implements ClientAction {
    String client;
    long timestamp = System.currentTimeMillis();

    String clientId;
    String consumer;
    String handler;
    String payloadType;
    boolean exceptionalResult;
    long nanosecondDuration;
}
