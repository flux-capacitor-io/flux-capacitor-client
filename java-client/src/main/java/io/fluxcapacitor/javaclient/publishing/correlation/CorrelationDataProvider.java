package io.fluxcapacitor.javaclient.publishing.correlation;

import java.util.Map;

@FunctionalInterface
public interface CorrelationDataProvider {
    Map<String, String> getCorrelationData();
}
