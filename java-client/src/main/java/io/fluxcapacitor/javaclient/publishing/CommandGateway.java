package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.concurrent.CompletableFuture;

public interface CommandGateway extends HasLocalHandlers {

    void sendAndForget(Object command);

    void sendAndForget(Object payload, Metadata metadata);

    <R> CompletableFuture<R> send(Object command);

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    CompletableFuture<Message> sendForMessage(Message message);

    <R> R sendAndWait(Object command);

    <R> R sendAndWait(Object payload, Metadata metadata);
}
