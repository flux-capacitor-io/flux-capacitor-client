package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class DefaultQueryGateway implements QueryGateway {

    private final GatewayClient queryGateway;
    private final RequestHandler requestHandler;
    private final MessageSerializer serializer;

    @Override
    public <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(serializer.serialize(payload, metadata), queryGateway::send)
                    .thenApply(s -> serializer.deserialize(s).getPayload());
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send query %s", payload), e);
        }
    }
}
