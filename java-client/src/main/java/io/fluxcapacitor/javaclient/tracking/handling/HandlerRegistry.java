package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface HandlerRegistry extends HasLocalHandlers {
    Optional<CompletableFuture<Message>> handle(Object payload, SerializedMessage serializedMessage);
}
