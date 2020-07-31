package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    private final GatewayClient gatewayClient;
    private final MessageSerializer serializer;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    @SneakyThrows
    public void publish(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        Optional<CompletableFuture<Message>> result =
                localHandlerRegistry.handle(message.getPayload(), serializedMessage);
        if (result.isPresent() && result.get().isCompletedExceptionally()) {
            try {
                result.get().getNow(null);
            } catch (CompletionException e) {
                throw e.getCause();
            }
        }
        try {
            gatewayClient.send(serializedMessage);
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
        }
    }
}
