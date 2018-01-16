package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Metadata;

import java.util.concurrent.CompletableFuture;

public interface MessageGateway {

    Awaitable send(Object payload, Metadata metadata);

    <R> CompletableFuture<R> sendAsRequest(Object payload, Metadata metadata);

}
