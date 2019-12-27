package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import lombok.Value;

@Value
public class HandleMessageEvent implements ClientEvent {
    String client;
    String clientId;
    long timestamp = System.currentTimeMillis();

    String consumer;
    String handler;
    Long messageIndex;
    String payloadType;
    boolean exceptionalResult;
    long nanosecondDuration;

}
