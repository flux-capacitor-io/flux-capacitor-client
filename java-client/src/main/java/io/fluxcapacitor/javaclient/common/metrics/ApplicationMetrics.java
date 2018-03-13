package io.fluxcapacitor.javaclient.common.metrics;

import io.fluxcapacitor.common.api.ClientAction;
import lombok.Value;

@Value
public class ApplicationMetrics implements ClientAction {
    String client;
    long timestamp = System.currentTimeMillis();

    String clientId;

    double cpuUsage;
    long memoryUsage;
    int threadCount;
    double averageGarbageCollectionTime;

}
