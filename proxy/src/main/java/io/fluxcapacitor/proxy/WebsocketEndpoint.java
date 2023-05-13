/*
 * Copyright (c) 2016-2022 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
 */

package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.DisconnectEvent;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import io.fluxcapacitor.javaclient.web.HttpRequestMethod;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.ObjectUtils.getBytes;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;
import static jakarta.websocket.CloseReason.CloseCodes.getCloseCode;
import static java.lang.String.format;

@Slf4j
@AllArgsConstructor
public class WebsocketEndpoint extends Endpoint {
    static final String metadataPrefix = "_metadata:", clientIdKey = "_clientId", trackerIdKey = "_trackerId";

    private final Map<String, Session> openSessions = new ConcurrentHashMap<>();

    private final Client client;
    private final GatewayClient requestGateway;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Registration registration;

    public WebsocketEndpoint(Client client) {
        this.client = client;
        this.requestGateway = client.getGatewayClient(MessageType.WEBREQUEST);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        ensureStarted();
        openSessions.put(session.getId(), session);
        session.addMessageHandler(byte[].class, bytes -> sendRequest(session, HttpRequestMethod.WS_MESSAGE, bytes));
        session.addMessageHandler(String.class, s -> sendRequest(session, HttpRequestMethod.WS_MESSAGE, s.getBytes()));
        session.addMessageHandler(PongMessage.class, pong -> sendRequest(session, HttpRequestMethod.WS_PONG,
                                                                         getBytes(pong.getApplicationData())));
        sendRequest(session, HttpRequestMethod.WS_OPEN, null);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        openSessions.remove(session.getId());
        sendRequest(session, HttpRequestMethod.WS_CLOSE,
                    String.valueOf(closeReason.getCloseCode().getCode()).getBytes());
    }

    @Override
    public void onError(Session session, Throwable error) {
        log.warn("Error in session {}", session.getId(), error);
    }

    protected void sendRequest(Session session, HttpRequestMethod method, byte[] payload) {
        Metadata metadata = getContext(session).metadata().with("method", method.name());
        var request = new SerializedMessage(new Data<>(payload, null, 0, "unknown"),
                                            metadata, FluxCapacitor.generateId(),
                                            FluxCapacitor.currentClock().millis());
        request.setSource(client.id());
        request.setTarget(getContext(session).trackerId());
        requestGateway.send(Guarantee.SENT, request);
    }

    protected void handleResultMessages(List<SerializedMessage> resultMessages) {
        resultMessages.forEach(m -> {
            var sessionId = m.getMetadata().get("sessionId");
            if (sessionId != null) {
                var session = openSessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        switch (m.getMetadata().getOrDefault("function", "message")) {
                            case "message" -> sendMessage(m, session);
                            case "ping" -> sendPing(m, session);
                            case "close" -> sendClose(m, session);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send websocket result to client (session {})", session.getId(), e);
                    }
                }
            }
        });
    }

    @SneakyThrows
    private void sendMessage(SerializedMessage m, Session session) {
        if (byte[].class.getName().equals(m.getData().getType())) {
            try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
                outputStream.write(m.getData().getValue());
            }
        } else {
            try (Writer writer = session.getBasicRemote().getSendWriter()) {
                writer.write(new String(m.getData().getValue()));
            }
        }
    }

    @SneakyThrows
    private void sendPing(SerializedMessage m, Session session) {
        session.getBasicRemote().sendPing(ByteBuffer.wrap(m.getData().getValue()));
    }

    @SneakyThrows
    private void sendClose(SerializedMessage m, Session session) {
        session.close(new CloseReason(getCloseCode(Integer.parseInt(new String(m.getData().getValue()))), null));
    }

    protected void handleDisconnects(List<SerializedMessage> resultMessages) {
        Set<String> clientIds =
                resultMessages.stream().map(m -> JsonUtils.fromJson(m.getData().getValue(), DisconnectEvent.class))
                        .map(DisconnectEvent::getClientId).collect(Collectors.toSet());
        openSessions.values().stream().filter(s -> clientIds.contains(getContext(s).clientId())).forEach(
                session -> {
                    try {
                        if (session.isOpen()) {
                            session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "going away"));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to close session {}", session.getId(), e);
                    }
                });
    }

    protected SessionContext getContext(Session session) {
        return (SessionContext) session.getUserProperties().computeIfAbsent("context", c -> {
            var contextBuilder = SessionContext.builder();
            Map<String, String> map = new LinkedHashMap<>();
            session.getRequestParameterMap().forEach((k, v) -> {
                if (k.startsWith(metadataPrefix)) {
                    String name = k.substring(metadataPrefix.length());
                    map.put(name, v.get(0));
                } else if (k.equals(trackerIdKey)) {
                    contextBuilder.trackerId(v.get(0));
                } else if (k.equals(clientIdKey)) {
                    contextBuilder.clientId(v.get(0));
                }
            });
            contextBuilder.metadata(Metadata.of(map).with("sessionId", session.getId()));
            return contextBuilder.build();
        });
    }

    protected void ensureStarted() {
        if (started.compareAndSet(false, true)) {
            registration = start(this::handleResultMessages, MessageType.WEBRESPONSE,
                                 ConsumerConfiguration.builder()
                                         .name(format("%s_%s", client.name(), "$websocket-handler")).ignoreSegment(true)
                                         .clientControlledIndex(true).filterMessageTarget(true)
                                         .minIndex(IndexUtils.indexFromTimestamp(
                                                 FluxCapacitor.currentTime().minusSeconds(1))).build(),
                                 client).merge(start(this::handleDisconnects, MessageType.METRICS,
                                                     ConsumerConfiguration.builder()
                                                             .name(format("%s_%s", client.name(), "$websocket-handler"))
                                                             .ignoreSegment(true)
                                                             .clientControlledIndex(true)
                                                             .typeFilter(Pattern.quote(DisconnectEvent.class.getName()))
                                                             .minIndex(IndexUtils.indexFromTimestamp(
                                                                     FluxCapacitor.currentTime().minusSeconds(1)))
                                                             .build(),
                                                     client));
        }
    }

    public void shutDown() {
        if (started.compareAndSet(true, false) && registration != null) {
            registration.cancel();
            openSessions.values().removeIf(s -> {
                try {
                    if (s.isOpen()) {
                        s.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, null));
                    }
                } catch (Throwable e) {
                    log.warn("Failed to close session when leaving: {}", s.getId(), e);
                }
                return true;
            });
        }
    }

    @Builder
    record SessionContext(Metadata metadata, String clientId, String trackerId) {
    }
}
