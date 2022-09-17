package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import javax.annotation.Nullable;
import java.util.Map;

@FunctionalInterface
public interface CorrelationDataProvider {
    default Map<String, String> getCorrelationData() {
        return getCorrelationData(DeserializingMessage.getCurrent());
    }

    Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage);

    default CorrelationDataProvider andThen(CorrelationDataProvider next) {
        return currentMessage -> {
            Map<String, String> result = getCorrelationData(currentMessage);
            result.putAll(next.getCorrelationData(currentMessage));
            return result;
        };
    }

}
