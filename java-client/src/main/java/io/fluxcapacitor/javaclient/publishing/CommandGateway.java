package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;

public interface CommandGateway {

    default void sendAndForget(Object payload) {
        sendAndForget(payload, Metadata.empty());
    }

    void sendAndForget(Object payload, Metadata metadata);

    default <R> CompletableFuture<R> send(Object payload) {
        return send(payload, Metadata.empty());
    }

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    default <R> R sendAndWait(Object payload) {
        return sendAndWait(payload, Metadata.empty());
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
