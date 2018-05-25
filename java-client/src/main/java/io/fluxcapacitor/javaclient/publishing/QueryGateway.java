package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.concurrent.CompletableFuture;

public interface QueryGateway {

    <R> CompletableFuture<R> send(Object query);

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    CompletableFuture<Message> sendForMessage(Object payload, Metadata metadata);

    <R> R sendAndWait(Object query);

    <R> R sendAndWait(Object payload, Metadata metadata);

    Registration registerLocalHandler(Object handler);

}
