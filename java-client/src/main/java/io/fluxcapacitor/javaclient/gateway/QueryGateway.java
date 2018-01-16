package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;

public interface QueryGateway {

    default <R> CompletableFuture<R> query(Object payload) {
        return query(payload, Metadata.empty());
    }

    <R> CompletableFuture<R> query(Object payload, Metadata metadata);

    default <R> R queryAndWait(Object payload) {
        return queryAndWait(payload, Metadata.empty());
    }

    @SneakyThrows
    default <R> R queryAndWait(Object payload, Metadata metadata) {
        CompletableFuture<R> future = query(payload, metadata);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new GatewayException(format("Thread interrupted while waiting for result of query %s", payload), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

}
