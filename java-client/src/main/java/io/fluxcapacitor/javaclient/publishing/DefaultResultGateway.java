package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultResultGateway implements ResultGateway {

    private final GatewayClient client;
    private final MessageSerializer serializer;

    @Override
    public void respond(Object payload, Metadata metadata, String target, int requestId) {
        try {
            SerializedMessage message = serializer.serialize(new Message(payload, metadata, MessageType.RESULT));
            message.setTarget(target);
            message.setRequestId(requestId);
            client.send(message);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send response %s", payload), e);
        }
    }
}
