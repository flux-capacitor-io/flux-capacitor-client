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

package io.fluxcapacitor.javaclient.common.connection;

import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractWebsocketService {
    private final Map<Long, CompletableFuture<QueryResult>> callbacks = new ConcurrentHashMap<>();
    private final Supplier<Session> sessionSupplier;

    public AbstractWebsocketService(URI endpointUri) {
        this.sessionSupplier = new SingleSessionSupplier(endpointUri, this);
    }

    public AbstractWebsocketService(Supplier<Session> sessionSupplier) {
        this.sessionSupplier = sessionSupplier;
    }

    protected Session getSession() {
        return sessionSupplier.get();
    }

    @SuppressWarnings("unchecked")
    protected <R extends QueryResult> R sendRequest(Request request) {
        CompletableFuture<QueryResult> future = new CompletableFuture<>();
        callbacks.put(request.getRequestId(), future);
        try {
            getSession().getBasicRemote().sendObject(request);
            return (R) future.get();
        } catch (Exception e) {
            callbacks.remove(request.getRequestId());
            throw new IllegalStateException("Failed to handle request " + request, e);
        }
    }

    @OnMessage
    public void onMessage(JsonType value) {
        QueryResult readResult = (QueryResult) value;
        CompletableFuture<QueryResult> callback = callbacks.remove(readResult.getRequestId());
        if (callback == null) {
            log.warn("Could not find outstanding read request for id {}", readResult.getRequestId());
        } else {
            callback.complete(readResult);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        log.info("Connection to endpoint {} closed with reason {}", session.getRequestURI(), closeReason);
        getSession();
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log.error("Client side error for web socket connected to endpoint {}", session.getRequestURI(), e);
    }

}
