/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.DefaultRequestHandler;
import io.fluxcapacitor.javaclient.publishing.RequestHandler;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.web.HttpRequestMethod;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import io.undertow.Undertow;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.servlet.DispatcherType;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.ObjectUtils.unwrapException;
import static io.fluxcapacitor.javaclient.web.WebUtils.fixHeaderName;
import static io.undertow.servlet.Servlets.deployment;
import static java.lang.String.format;

@Slf4j
public class ProxyRequestHandler implements HttpHandler, AutoCloseable {
    private final ProxySerializer serializer = new ProxySerializer();
    private final GatewayClient requestGateway;
    private final RequestHandler requestHandler;
    private final WebsocketEndpoint websocketEndpoint;
    private final HttpHandler websocketHandler;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ProxyRequestHandler(Client client) {
        requestGateway = client.getGatewayClient(MessageType.WEBREQUEST);
        requestHandler = new DefaultRequestHandler(client, MessageType.WEBRESPONSE, Duration.ofSeconds(200),
                                                   format("%s_%s", client.name(), "$proxy-request-handler"));
        websocketEndpoint = new WebsocketEndpoint(client);
        websocketHandler = createWebsocketHandler();
    }

    @Override
    @SneakyThrows
    public void handleRequest(HttpServerExchange exchange) {
        if (closed.get()) {
            throw new IllegalStateException("Request handler has been shut down and is not accepting new requests");
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.getRequestReceiver().receiveFullBytes(
                (se, payload) -> se.dispatch(() -> {
                    try {
                        sendWebRequest(se, createWebRequest(se, payload));
                    } catch (Throwable e) {
                        log.error("Failed to create request", e);
                        sendServerError(se);
                    }
                }),
                (se, error) -> se.dispatch(() -> {
                    log.error("Failed to read incoming message", error);
                    sendServerError(se);
                }));
    }

    protected WebRequest createWebRequest(HttpServerExchange se, byte[] payload) {
        var builder = WebRequest.builder()
                .url(se.getRelativePath() + (se.getQueryString().isBlank() ? "" : ("?" + se.getQueryString())))
                .method(HttpRequestMethod.valueOf(se.getRequestMethod().toString())).payload(payload)
                .acceptGzipEncoding(false);
        se.getRequestHeaders().forEach(
                header -> header.forEach(value -> builder.header(fixHeaderName(
                        header.getHeaderName().toString()), value)));
        return tryUpgrade(builder.build());
    }

    protected WebRequest tryUpgrade(WebRequest webRequest) {
        if (webRequest.getMethod() == HttpRequestMethod.GET
            && "Upgrade".equalsIgnoreCase(webRequest.getHeader("Connection"))
            && "websocket".equalsIgnoreCase(webRequest.getHeader("Upgrade"))) {
            return webRequest.toBuilder().method(HttpRequestMethod.WS_HANDSHAKE).build();
        }
        return webRequest;
    }

    protected void sendWebRequest(HttpServerExchange se, WebRequest webRequest) {
        SerializedMessage requestMessage = webRequest.serialize(serializer);
        requestHandler.sendRequest(requestMessage, m -> requestGateway.send(Guarantee.SENT, m))
                .whenComplete((r, e) -> {
                    try {
                        e = unwrapException(e);
                        if (e == null) {
                            handleResponse(r, webRequest, se);
                        } else if (e instanceof TimeoutException) {
                            log.warn("Request {} timed out (messageId: {}). This is possibly due to a missing handler.",
                                      webRequest, webRequest.getMessageId(), e);
                            sendGatewayTimeout(se);
                        } else {
                            log.error("Failed to complete {} (messageId: {})",
                                      webRequest, webRequest.getMessageId(), e);
                            sendServerError(se);
                        }
                    } catch (Throwable t) {
                        log.error("Failed to process response {} to request {}", e == null ? r : e, webRequest, t);
                    }
                });
    }

    @SneakyThrows
    protected void handleResponse(SerializedMessage responseMessage, WebRequest webRequest, HttpServerExchange se) {
        int statusCode = WebResponse.getStatusCode(responseMessage.getMetadata());
        if (statusCode < 300 && webRequest.getMethod() == HttpRequestMethod.WS_HANDSHAKE) {
            se.addQueryParam("_clientId", responseMessage.getMetadata().get("clientId"));
            se.addQueryParam("_trackerId", responseMessage.getMetadata().get("trackerId"));
            websocketHandler.handleRequest(se);
            return;
        }
        boolean http2 = se.getProtocol().compareTo(Protocols.HTTP_1_1) > 0;
        se.setStatusCode(statusCode);
        WebResponse.getHeaders(responseMessage.getMetadata()).forEach(
                (key, value) -> {
                    if (http2 || !key.startsWith(":")) {
                        se.getResponseHeaders().addAll(new HttpString(key), value);
                    }
                });
        Optional.ofNullable(responseMessage.getData().getFormat()).ifPresent(
                format -> se.getResponseHeaders().add(new HttpString("Content-Type"), format));
        se.getResponseSender().send(ByteBuffer.wrap(responseMessage.getData().getValue()));
    }

    protected void sendServerError(HttpServerExchange se) {
        se.setStatusCode(500);
        se.getResponseSender().send("Request could not be handled due to a server side error");
    }

    protected void sendGatewayTimeout(HttpServerExchange se) {
        se.setStatusCode(504);
        se.getResponseSender().send("Did not receive a response in time");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            websocketEndpoint.shutDown();
            requestHandler.close();
            requestGateway.close();
        }
    }

    @SneakyThrows
    protected HttpHandler createWebsocketHandler() {
        DeploymentManager deploymentManager = Servlets.defaultContainer().addDeployment(
                deployment().setContextPath("/**").addServletContextAttribute(
                                WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                                new WebSocketDeploymentInfo()
                                        .setBuffers(new DefaultByteBufferPool(false, 1024, 100, 12))
                                        .setWorker(Xnio.getInstance().createWorker(
                                                OptionMap.create(Options.THREAD_DAEMON, true)))
                                        .addEndpoint(ServerEndpointConfig.Builder
                                                             .create(WebsocketEndpoint.class, "/**")
                                                             .configurator(new ServerEndpointConfig.Configurator() {
                                                                 @Override
                                                                 public <T> T getEndpointInstance(Class<T> endpointClass) {
                                                                     return endpointClass.cast(websocketEndpoint);
                                                                 }
                                                             }).build()))
                        .setDeploymentName("websocket")
                        .addFilter(new FilterInfo("websocketFilter", WebsocketFilter.class))
                        .addFilterUrlMapping("websocketFilter", "*", DispatcherType.REQUEST)
                        .setClassLoader(Undertow.class.getClassLoader()));
        deploymentManager.deploy();
        return deploymentManager.start();
    }

}
