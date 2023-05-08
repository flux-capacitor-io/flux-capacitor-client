package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;

import java.util.concurrent.CompletableFuture;

public record DefaultSocketSession(String sessionId, String target,
                                   ResultGateway webResponseGateway) implements SocketSession {

    @Override
    public CompletableFuture<Void> sendMessage(Object value, Guarantee guarantee) {
        return sendMessage(Message.asMessage(value).addMetadata("function", "message"), guarantee);
    }

    @Override
    public CompletableFuture<Void> sendPing(Object value, Guarantee guarantee) {
        return sendMessage(Message.asMessage(value).addMetadata("function", "ping"), guarantee);
    }

    @Override
    public CompletableFuture<Void> close(int code, Guarantee guarantee) {
        if (code < 1000 || code > 4999) {
            throw new IllegalArgumentException("Invalid code: " + code);
        }
        return sendMessage(Message.asMessage(String.valueOf(code)).addMetadata("function", "close"), guarantee);
    }

    CompletableFuture<Void> sendMessage(Message message, Guarantee guarantee) {
        return webResponseGateway.respond(message.getPayload(), message.getMetadata().with("sessionId", sessionId),
                                          target, null, guarantee);
    }
}
