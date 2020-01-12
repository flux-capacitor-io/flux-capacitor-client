package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class DefaultMetricsGateway implements MetricsGateway {

    private final GatewayClient metricsGateway;
    private final MessageSerializer serializer;

    @Override
    public void publish(Object payload, Metadata metadata) {
        try {
            metricsGateway.send(serializer.serialize(new Message(payload, metadata)));
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to publish metrics %s", payload), e);
        }
    }
}
