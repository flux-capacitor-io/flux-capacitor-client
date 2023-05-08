package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;

import java.util.concurrent.CompletableFuture;

public interface SocketSession {

    String sessionId();

    default void sendMessage(Object value) {
        sendMessage(value, Guarantee.NONE);
    }

    CompletableFuture<Void> sendMessage(Object value, Guarantee guarantee);

    default void sendPing(Object value) {
        sendPing(value, Guarantee.NONE);
    }

    CompletableFuture<Void> sendPing(Object value, Guarantee guarantee);

    default void close() {
        close(Guarantee.NONE);
    }

    default CompletableFuture<Void> close(Guarantee guarantee) {
        return close(1000, guarantee);
    }

    CompletableFuture<Void> close(int closeReason, Guarantee guarantee);
}
