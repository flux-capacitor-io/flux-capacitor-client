package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;

public interface CommandGateway {

    default void sendAndForget(Message message) {
        sendAndForget(message.getPayload(), message.getMetadata());
    }

    default void sendAndForget(Object command) {
        if (command instanceof Message) {
            sendAndForget(((Message) command).getPayload(), ((Message) command).getMetadata());
        } else {
            sendAndForget(command, Metadata.empty());
        }
    }

    void sendAndForget(Object payload, Metadata metadata);

    default <R> CompletableFuture<R> send(Object command) {
        if (command instanceof Message) {
            return send(((Message) command).getPayload(), ((Message) command).getMetadata());
        } else {
            return send(command, Metadata.empty());
        }
    }

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    default <R> R sendAndWait(Object command) {
        if (command instanceof Message) {
            return sendAndWait(((Message) command).getPayload(), ((Message) command).getMetadata());
        } else {
            return sendAndWait(command, Metadata.empty());
        }
    }

    @SneakyThrows
    default <R> R sendAndWait(Object payload, Metadata metadata) {
        CompletableFuture<R> future = send(payload, metadata);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new GatewayException(format("Thread interrupted while waiting for result of command %s", payload), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

}
