package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.SerializedMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RequestHandler {

    <R> CompletableFuture<R> sendRequest(SerializedMessage request, Consumer<SerializedMessage> requestSender);

}
