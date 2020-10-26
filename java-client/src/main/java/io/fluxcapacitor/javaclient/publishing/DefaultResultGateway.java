package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

@AllArgsConstructor
public class DefaultResultGateway implements ResultGateway {

    private final GatewayClient client;
    private final MessageSerializer serializer;

    @Override
    public void respond(Object payload, Metadata metadata, String target, int requestId) {
        try {
            if (payload instanceof TechnicalException) {
                metadata = metadata.with("stackTrace", ExceptionUtils.getStackTrace((TechnicalException) payload));
            }
            SerializedMessage message = serializer.serialize(new Message(payload, metadata));
            message.setTarget(target);
            message.setRequestId(requestId);
            client.send(message);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send response %s", payload), e);
        }
    }
}
