package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultResultGateway implements ResultGateway {

    private final GatewayClient client;
    private final Serializer serializer;

    @Override
    public void respond(Object payload, Metadata metadata, String target, int requestId) {
        try {
            SerializedMessage message = new SerializedMessage(serializer.serialize(payload), metadata);
            message.setTarget(target);
            message.setRequestId(requestId);
            client.send(message);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send response %s", payload), e);
        }
    }
}
