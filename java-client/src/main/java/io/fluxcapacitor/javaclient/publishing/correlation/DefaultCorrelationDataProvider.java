package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

@Getter
public enum DefaultCorrelationDataProvider implements CorrelationDataProvider {
    INSTANCE;

    private final String clientIdKey = "$clientId", clientNameKey = "$clientName", consumerKey = "$consumer",
            correlationIdKey = "$correlationId", traceIdKey = "$traceId", triggerKey = "$trigger";

    @Override
    public Map<String, String> getCorrelationData(DeserializingMessage currentMessage) {
        Map<String, String> result = new HashMap<>();
        FluxCapacitor.getOptionally().ifPresent(f -> {
            result.put(clientIdKey, f.client().id());
            result.put(clientNameKey, f.client().name());
        });
        Tracker.current().ifPresent(t -> result.put(consumerKey, t.getName()));
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put(this.correlationIdKey, correlationId);
            result.put(traceIdKey, currentMessage.getMetadata().getOrDefault(traceIdKey, correlationId));
            result.put(triggerKey, m.getType());
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        return result;
    }
}
