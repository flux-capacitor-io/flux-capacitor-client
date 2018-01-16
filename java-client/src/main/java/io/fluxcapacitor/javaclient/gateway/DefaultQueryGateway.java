package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.tracking.RequestHandler;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class DefaultQueryGateway implements QueryGateway {

    private final GatewayClient commandGateway;
    private final RequestHandler requestHandler;
    private final Serializer serializer;

    @Override
    public <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(new SerializedMessage(serializer.serialize(payload), metadata),
                                              commandGateway::send);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send query %s", payload), e);
        }
    }
}
