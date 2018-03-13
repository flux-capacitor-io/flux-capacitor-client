package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.ClientAction;
import lombok.Value;

@Value
public class ProcessBatchAction implements ClientAction {
    String client;
    long timestamp = System.currentTimeMillis();

    String clientId;
    String consumer;
    int channel;
    int batchSize;
    long nanosecondDuration;
}
