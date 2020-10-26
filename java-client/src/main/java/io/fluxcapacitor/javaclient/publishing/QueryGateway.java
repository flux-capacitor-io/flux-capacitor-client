package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.concurrent.CompletableFuture;

public interface QueryGateway extends HasLocalHandlers {

    <R> CompletableFuture<R> send(Object query);

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    CompletableFuture<Message> sendForMessage(Message message);

    <R> R sendAndWait(Object query);

    <R> R sendAndWait(Object payload, Metadata metadata);

}
