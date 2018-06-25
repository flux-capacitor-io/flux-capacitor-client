package io.fluxcapacitor.javaclient.common.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import lombok.Value;

@Value
public class ApplicationMetricsEvent implements ClientEvent {
    String client;
    String clientId;
    long timestamp = System.currentTimeMillis();

    double cpuUsage;
    long memoryUsage;
    int threadCount;
    double averageGarbageCollectionTime;

}
