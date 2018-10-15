package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.concurrent.CompletableFuture;

public interface CommandGateway {

    void sendAndForget(Message message);

    void sendAndForget(Object command);

    void sendAndForget(Object payload, Metadata metadata);

    <R> CompletableFuture<R> send(Object command);

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    CompletableFuture<Message> sendForMessage(Message message);

    <R> R sendAndWait(Object command);

    <R> R sendAndWait(Object payload, Metadata metadata);
    
    Registration registerLocalHandler(Object handler);

}
