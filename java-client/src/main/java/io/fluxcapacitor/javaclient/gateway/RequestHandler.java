package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.SerializedMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RequestHandler {

   CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request, Consumer<SerializedMessage> requestSender);

}
