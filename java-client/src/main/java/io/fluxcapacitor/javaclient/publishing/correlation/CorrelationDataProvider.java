package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.Map;

public interface CorrelationDataProvider {
    default Map<String, String> getCorrelationData() {
        return getCorrelationData(DeserializingMessage.getCurrent());
    }

    Map<String, String> getCorrelationData(DeserializingMessage currentMessage);

    Map<String, String> getCorrelationData(SerializedMessage currentMessage);
}
