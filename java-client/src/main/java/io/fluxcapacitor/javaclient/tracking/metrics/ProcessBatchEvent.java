package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import lombok.Value;

@Value
public class ProcessBatchEvent implements ClientEvent {
    String client;
    String clientId;
    long timestamp = System.currentTimeMillis();

    String consumer;
    int channel;
    int batchSize;
    long nanosecondDuration;

}
