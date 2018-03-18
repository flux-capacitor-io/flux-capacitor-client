package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.String.format;

public interface QueryGateway {

    default <R> CompletableFuture<R> query(Object query) {
        if (query instanceof Message) {
            return query(((Message) query).getPayload(), ((Message) query).getMetadata());
        } else {
            return query(query, Metadata.empty());
        }
    }

    default <R> CompletableFuture<R> query(Object payload, Metadata metadata) {
        return queryForMessage(payload, metadata).thenApply(Message::getPayload);
    }

    CompletableFuture<Message> queryForMessage(Object payload, Metadata metadata);

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
            Timeout timeout = payload.getClass().getAnnotation(Timeout.class);
            if (timeout != null) {
                return future.get(timeout.millis(), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException e) {
            throw new io.fluxcapacitor.javaclient.publishing.TimeoutException(
                    format("Query %s has timed out", payload), e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new GatewayException(format("Thread interrupted while waiting for result of query %s", payload), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

}
