package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import lombok.Value;

@Value
public class ProcessBatchEvent implements ClientEvent {
    String client;
    String clientId;
    long timestamp = System.currentTimeMillis();

    String consumer;
    String trackerId;
    Long lastIndex;
    int batchSize;
    long nanosecondDuration;

}
