package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {

    private final GatewayClient eventGateway;
    private final MessageSerializer serializer;

    @Override
    public void publishEvent(Object payload, Metadata metadata) {
        try {
            eventGateway.send(serializer.serialize(payload, metadata));
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to publish event %s", payload), e);
        }
    }
}
