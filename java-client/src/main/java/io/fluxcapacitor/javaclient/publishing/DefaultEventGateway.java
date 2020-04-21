package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    private final GatewayClient gatewayClient;
    private final MessageSerializer serializer;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public void publish(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        localHandlerRegistry.handle(message.getPayload(), serializedMessage);
        try {
            gatewayClient.send(serializedMessage);
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
        }
    }
}
