/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.common.websocket;

import io.fluxcapacitor.common.TimingUtils;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.tyrus.client.ClientManager;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractWebsocketClient {
    private final ClientManager client;
    private final URI endpointUri;
    private final Duration reconnectDelay;
    private final Map<Long, WebSocketRequest> requests = new ConcurrentHashMap<>();
    private final AtomicReference<Session> session = new AtomicReference<>();

    public AbstractWebsocketClient(URI endpointUri) {
        this(ClientManager.createClient(), endpointUri, Duration.ofSeconds(1));
    }

    public AbstractWebsocketClient(ClientManager client, URI endpointUri, Duration reconnectDelay) {
        this.client = client;
        this.endpointUri = endpointUri;
        this.reconnectDelay = reconnectDelay;
    }

    @SuppressWarnings("unchecked")
    protected <R extends QueryResult> R sendRequest(Request request) {
        WebSocketRequest webSocketRequest = new WebSocketRequest(request);
        requests.put(request.getRequestId(), webSocketRequest);
        try {
            webSocketRequest.send(getSession());
            return (R) webSocketRequest.get();
        } catch (Exception e) {
            requests.remove(request.getRequestId());
            throw new IllegalStateException("Failed to handle request " + request, e);
        }
    }

    @OnMessage
    public void onMessage(JsonType value) {
        QueryResult readResult = (QueryResult) value;
        WebSocketRequest webSocketRequest = requests.remove(readResult.getRequestId());
        if (webSocketRequest == null) {
            log.warn("Could not find outstanding read request for id {}", readResult.getRequestId());
        } else {
            webSocketRequest.complete(readResult);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        log.info("Connection to endpoint {} closed with reason {}", session.getRequestURI(), closeReason);
        retryOutstandingRequests(session.getId());
    }

    protected void retryOutstandingRequests(String sessionId) {
        requests.values().stream().filter(r -> sessionId.equals(r.getSessionId())).forEach(r -> {
            try {
                r.send(getSession());
            } catch (Exception e) {
                r.completeExceptionally(e);
            }
        });
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log.error("Client side error for web socket connected to endpoint {}", session.getRequestURI(), e);
    }

    protected Session getSession() {
        return session.updateAndGet(s -> {
            while (s == null || !s.isOpen()) {
                s = TimingUtils.retryOnFailure(() -> client.connectToServer(this, endpointUri), reconnectDelay);
            }
            return s;
        });
    }

    @RequiredArgsConstructor
    @Getter
    protected static class WebSocketRequest implements Future<QueryResult> {
        private final Request request;
        @Delegate
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private volatile String sessionId;

        @SneakyThrows
        protected void send(Session session) {
            this.sessionId = session.getId();
            session.getBasicRemote().sendObject(request);
        }
    }

}
