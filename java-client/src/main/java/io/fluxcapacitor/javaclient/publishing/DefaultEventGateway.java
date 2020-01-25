package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    private final GatewayClient gatewayClient;
    private final MessageSerializer serializer;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        tryHandleLocally(message.getPayload(), serializedMessage);
        try {
            gatewayClient.send(serializedMessage);
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
        }
    }

    @Override
    public Registration registerLocalHandler(Object target) {
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(target);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    protected void tryHandleLocally(Object payload, SerializedMessage serializedMessage) {
        if (!localHandlers.isEmpty()) {
            new DeserializingMessage(serializedMessage, () -> payload, EVENT).run(m -> {
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    try {
                        if (handler.canHandle(m)) {
                            handler.invoke(m);
                        }
                    } finally {
                        handler.onEndOfBatch();
                    }
                }
            });
        }
    }
}
