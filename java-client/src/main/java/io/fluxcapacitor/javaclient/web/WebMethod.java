package io.fluxcapacitor.javaclient.web;

public enum WebMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE, WEBSOCKET, WEBSOCKET_OPEN, WEBSOCKET_CLOSE, UNKNOWN;

    public boolean isHttp() {
        return !isWebSocket();
    }

    public boolean isWebSocket() {
        return this != UNKNOWN && name().startsWith("WEBSOCKET");
    }
}
