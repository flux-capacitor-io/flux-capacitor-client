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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebRequestSettings;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.web.WebRequest.getHeaders;
import static java.util.Optional.ofNullable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ForwardProxyConsumer implements Consumer<List<SerializedMessage>> {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(5)).build();
    protected static final WebRequestSettings defaultSettings = WebRequestSettings.builder().build();
    protected static final Serializer serializer = new ProxySerializer();

    protected final Map<String, Registration> runningConsumers = new ConcurrentHashMap<>();

    private final Client client;
    private final String consumerName;
    private final Long minIndex;
    @Getter(lazy = true, value = AccessLevel.PROTECTED)
    private final boolean mainConsumer = minIndex == null;

    public static Registration start(Client client) {
        var consumer = new ForwardProxyConsumer(client, defaultSettings.getConsumer(), null);
        consumer.runningConsumers.computeIfAbsent(defaultSettings.getConsumer(), c -> consumer.start());
        return () -> {
            Collection<Registration> running = consumer.runningConsumers.values();
            running.forEach(Registration::cancel);
            running.clear();
        };
    }

    protected Registration start() {
        log.info(isMainConsumer() ? "Starting consumer {}" : "Starting consumer {} at {}", consumerName, minIndex);
        return DefaultTracker.start(this, MessageType.WEBREQUEST,
                                    ConsumerConfiguration.builder().name(consumerName).minIndex(minIndex).threads(4)
                                            .build(), client);
    }

    @Override
    public void accept(List<SerializedMessage> serializedMessages) {
        for (SerializedMessage s : serializedMessages) {
            try {
                var settings = getSettings(s);
                if (consumerName.equals(settings.getConsumer())) {
                    URI uri = URI.create(WebRequest.getUrl(s.getMetadata()));
                    if (uri.isAbsolute()) {
                        handle(s, uri, settings);
                    }
                } else if (isMainConsumer()) {
                    runningConsumers.computeIfAbsent(
                            settings.getConsumer(), c -> new ForwardProxyConsumer(client, c, s.getIndex()).start());
                }
            } catch (Throwable e) {
                log.error("Failed to handle external request {}. Continuing..", s.getMessageId(), e);
                try {
                    sendResponse(asWebResponse(e), s);
                } catch (Throwable e2) {
                    e2.addSuppressed(e);
                    log.error("Failed to send error response. Continuing..", e2);
                }
            }
        }
    }

    void handle(SerializedMessage request, URI uri, WebRequestSettings settings) {
        HttpRequest httpRequest = asHttpRequest(request, uri, settings);
        WebResponse webResponse = executeRequest(httpRequest);
        sendResponse(webResponse, request);
    }

    HttpRequest asHttpRequest(SerializedMessage request, URI uri, WebRequestSettings settings) {
        var builder = HttpRequest.newBuilder()
                .version(HttpClient.Version.valueOf(settings.getHttpVersion().name()))
                .timeout(settings.getTimeout());
        getHeaders(request.getMetadata()).forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
        builder.uri(uri).method(WebRequest.getMethod(request.getMetadata()).name(), getBodyPublisher(request));
        return builder.build();
    }

    protected WebRequestSettings getSettings(SerializedMessage request) {
        return Optional.ofNullable(request.getMetadata().get("settings", WebRequestSettings.class))
                .orElse(defaultSettings);
    }

    WebResponse executeRequest(HttpRequest httpRequest) {
        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return asWebResponse(response);
        } catch (Throwable e) {
            log.error("Failed to handle external request. Returning error.. ", e);
            return asWebResponse(e);
        }
    }

    void sendResponse(WebResponse response, SerializedMessage request) {
        SerializedMessage serializedResponse = new SerializedMessage(
                serializer.serialize(response.getPayload()).withFormat("application/octet-stream"),
                response.getMetadata(), response.getMessageId(), response.getTimestamp().toEpochMilli());
        serializedResponse.setRequestId(request.getRequestId());
        serializedResponse.setTarget(request.getSource());
        client.getGatewayClient(MessageType.WEBRESPONSE).append(Guarantee.NONE, serializedResponse);
    }

    WebResponse asWebResponse(HttpResponse<byte[]> response) {
        WebResponse.Builder builder = WebResponse.builder();
        response.headers().map().forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
        return builder.status(response.statusCode()).payload(response.body()).build();
    }

    WebResponse asWebResponse(Throwable e) {
        return WebResponse.builder().status(502).payload(
                ofNullable(e.getMessage()).orElse("Exception while handling request in proxy")
                        .getBytes()).build();
    }

    HttpRequest.BodyPublisher getBodyPublisher(SerializedMessage request) {
        String type = request.getData().getType();
        if (type == null || Void.class.getName().equals(type) || request.getData().getValue().length == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(request.data().getValue()));
    }
}
