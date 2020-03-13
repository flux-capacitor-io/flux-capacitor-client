package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@AllArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    private final MessageType messageType;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Override
    public Registration registerHandler(Object target) {
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(target, "local-" + messageType);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    @Override
    public Optional<CompletableFuture<Message>> handle(Object payload, SerializedMessage serializedMessage) {
        if (!localHandlers.isEmpty()) {
            return new DeserializingMessage(serializedMessage, () -> payload, messageType).apply(m -> {
                boolean handled = false;
                CompletableFuture<Message> future = new CompletableFuture<>();
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    if (handler.canHandle(m)) {
                        boolean passive = handler.isPassive(m);
                        try {
                            Object result = handler.invoke(m);
                            if (!passive && !future.isDone()) {
                                if (result instanceof CompletableFuture<?>) {
                                    future = ((CompletableFuture<?>) result).thenApply(Message::new);
                                } else {
                                    future.complete(new Message(result));
                                }
                            }
                        } catch (Exception e) {
                            if (passive) {
                                log.error("Passive local handler {} failed to handle a {}", handler,
                                          m.getPayloadClass(), e);
                            } else {
                                future.completeExceptionally(e);
                            }
                        } finally {
                            if (!passive) {
                                handled = true;
                            }
                        }
                    }
                }
                return handled ? Optional.of(future) : Optional.empty();
            });
        }
        return Optional.empty();
    }
}
