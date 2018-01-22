package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class DefaultCommandGateway implements CommandGateway {

    private final GatewayClient commandGateway;
    private final RequestHandler requestHandler;
    private final Serializer serializer;

    @Override
    public void sendAndForget(Object payload, Metadata metadata) {
        try {
            commandGateway.send(new SerializedMessage(serializer.serialize(payload), metadata));
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send and forget command %s", payload), e);
        }
    }

    @Override
    public <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(new SerializedMessage(serializer.serialize(payload), metadata),
                                              commandGateway::send).thenApply(s -> serializer.deserialize(s.getData()));
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send command %s", payload), e);
        }
    }
}
