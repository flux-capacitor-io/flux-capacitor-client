package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;

public interface QueryGateway {

    default <R> CompletableFuture<R> query(Object query) {
        if (query instanceof Message) {
            return query(((Message) query).getPayload(), ((Message) query).getMetadata());
        } else {
            return query(query, Metadata.empty());
        }
    }

    <R> CompletableFuture<R> query(Object payload, Metadata metadata);

    default <R> R queryAndWait(Object query) {
        if (query instanceof Message) {
            return queryAndWait(((Message) query).getPayload(), ((Message) query).getMetadata());
        } else {
            return queryAndWait(query, Metadata.empty());
        }
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
