package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.Map;

import static java.util.Optional.ofNullable;

@FunctionalInterface
public interface CorrelationDataProvider {
    default Map<String, String> getCorrelationData() {
        return getCorrelationData(ofNullable(DeserializingMessage.getCurrent())
                                          .map(DeserializingMessage::getSerializedObject).orElse(null));
    }

    Map<String, String> getCorrelationData(SerializedMessage currentMessage);
}
