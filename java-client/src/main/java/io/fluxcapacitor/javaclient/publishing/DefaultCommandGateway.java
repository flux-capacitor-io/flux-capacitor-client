package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class DefaultCommandGateway implements CommandGateway {

    private final GatewayClient commandGateway;
    private final RequestHandler requestHandler;
    private final MessageSerializer serializer;

    @Override
    public void sendAndForget(Object payload, Metadata metadata) {
        try {
            commandGateway.send(serializer.serialize(payload, metadata));
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send and forget command %s", payload), e);
        }
    }

    @Override
    public <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(serializer.serialize(payload, metadata), commandGateway::send)
                    .thenApply(Message::getPayload);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send command %s", payload), e);
        }
    }

    @Override
    public CompletableFuture<Message> sendForMessage(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(serializer.serialize(payload, metadata), commandGateway::send);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send command %s", payload), e);
        }
    }
}
