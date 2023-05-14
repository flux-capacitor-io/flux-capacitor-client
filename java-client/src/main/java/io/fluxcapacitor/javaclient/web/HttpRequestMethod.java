package io.fluxcapacitor.javaclient.web;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HttpRequestMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE,
    WS_HANDSHAKE(true), WS_OPEN(true), WS_MESSAGE(true), WS_CLOSE(true), WS_PONG(true);

    HttpRequestMethod() {
        this(false);
    }

    private final boolean websocket;
}
