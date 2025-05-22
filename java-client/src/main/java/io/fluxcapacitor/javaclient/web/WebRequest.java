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

package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.api.Data.JSON_FORMAT;
import static io.fluxcapacitor.javaclient.web.WebUtils.asHeaderMap;

/**
 * Represents a web request message within the Flux platform.
 * <p>
 * This message is routed to handlers using annotations like {@link HandleWeb}, {@link HandleGet}, or
 * {@link HandleSocketOpen}.
 * </p>
 *
 * <p>
 * {@code WebRequest} extends {@link Message} and includes additional metadata such as:
 * </p>
 * <ul>
 *   <li>{@code path} – the requested URI</li>
 *   <li>{@code method} – HTTP or WebSocket method (e.g. GET, WS_OPEN)</li>
 *   <li>{@code headers} – request headers</li>
 *   <li>{@code cookies} – parsed from the Cookie header</li>
 * </ul>
 *
 * <p>
 * It also provides a fluent {@code Builder} API to construct requests programmatically.
 * </p>
 *
 * @see WebResponse
 * @see HandleWeb
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"headers", "cookies"})
public class WebRequest extends Message {
    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderFromMetadata(Metadata metadata) {
        return new Builder(metadata);
    }

    @NonNull
    String path;
    @NonNull
    String method;
    @NonNull
    Map<String, List<String>> headers;

    @Getter(lazy = true)
    @JsonIgnore
    List<HttpCookie> cookies = Optional.ofNullable(getHeader("Cookie"))
            .map(WebUtils::parseRequestCookieHeader).orElse(Collections.emptyList());

    private WebRequest(Builder builder) {
        super(builder.payload(), builder.metadata.with("url", builder.url(), "method", builder.method(),
                                                       "headers", builder.headers()));
        this.path = builder.url();
        this.method = builder.method();
        this.headers = builder.headers();
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    WebRequest(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
        this.path = getUrl(metadata);
        this.method = getMethod(metadata);
        this.headers = getHeaders(metadata);
    }

    public WebRequest(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public SerializedMessage serialize(Serializer serializer) {
        return Optional.ofNullable(getContentType()).map(
                        format -> new SerializedMessage(serializer.serialize(getPayload(), format), getMetadata(),
                                                        getMessageId(), getTimestamp().toEpochMilli()))
                .orElseGet(() -> super.serialize(serializer));
    }

    @Override
    public WebRequest withMetadata(Metadata metadata) {
        return new WebRequest(super.withMetadata(metadata));
    }

    @Override
    public WebRequest addMetadata(Metadata metadata) {
        return (WebRequest) super.addMetadata(metadata);
    }

    @Override
    public WebRequest addMetadata(String key, Object value) {
        return (WebRequest) super.addMetadata(key, value);
    }

    @Override
    public WebRequest addMetadata(Object... keyValues) {
        return (WebRequest) super.addMetadata(keyValues);
    }

    @Override
    public WebRequest addMetadata(Map<String, ?> values) {
        return (WebRequest) super.addMetadata(values);
    }

    @Override
    public WebRequest addUser(User user) {
        return (WebRequest) super.addUser(user);
    }

    @Override
    public WebRequest withPayload(Object payload) {
        if (payload == getPayload()) {
            return this;
        }
        return toBuilder().payload(payload).build();
    }

    @Override
    public WebRequest withMessageId(String messageId) {
        return new WebRequest(super.withMessageId(messageId));
    }

    @Override
    public WebRequest withTimestamp(Instant timestamp) {
        return new WebRequest(super.withTimestamp(timestamp));
    }

    public String getHeader(String name) {
        return getHeaders(name).stream().findFirst().orElse(null);
    }

    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name, Collections.emptyList());
    }

    public String getContentType() {
        return getHeader("Content-Type");
    }

    @Override
    public <R> R getPayloadAs(Type type) {
        return JSON_FORMAT.equalsIgnoreCase(getContentType())
                ? JsonUtils.convertValue(getPayload(), type)
                : super.getPayloadAs(type);
    }

    public Optional<HttpCookie> getCookie(String name) {
        return getCookies().stream().filter(c -> Objects.equals(name, c.getName())).findFirst();
    }

    public WebRequest.Builder toBuilder() {
        return new Builder(this);
    }

    public static String getUrl(Metadata metadata) {
        return Optional.ofNullable(metadata.get("url")).map(u -> u.startsWith("/") || u.contains("://") ? u : "/" + u)
                .orElseThrow(() -> new IllegalStateException("WebRequest is malformed: url is missing"));
    }

    public static String getMethod(Metadata metadata) {
        return Optional.ofNullable(metadata.get("method"))
                .orElseThrow(() -> new IllegalStateException("WebRequest is malformed: http method is missing"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return Optional.ofNullable(metadata.get("headers", Map.class))
                .map(map -> asHeaderMap(map))
                .orElseGet(WebUtils::emptyHeaderMap);
    }

    public static Optional<String> getHeader(Metadata metadata, String name) {
        return getHeaders(metadata).getOrDefault(name, Collections.emptyList()).stream().findFirst();
    }

    public static Optional<HttpCookie> getCookie(Metadata metadata, String name) {
        return getHeaders(metadata).getOrDefault("Cookie", Collections.emptyList())
                .stream().findFirst().map(WebUtils::parseRequestCookieHeader).orElseGet(Collections::emptyList)
                .stream().filter(c -> Objects.equals(c.getName(), name)).findFirst();
    }

    public static String getSocketSessionId(Metadata metadata) {
        return metadata.get("sessionId");
    }

    public static String requireSocketSessionId(Metadata metadata) {
        return metadata.getOrThrow("sessionId", () -> new IllegalStateException(
                "`sessionId` is missing in the metadata of the WebRequest"));
    }

    @Data
    @NoArgsConstructor
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        String url;
        String method;
        final Map<String, List<String>> headers = WebUtils.emptyHeaderMap();
        boolean acceptGzipEncoding = true;

        @Setter(AccessLevel.NONE)
        List<HttpCookie> cookies = new ArrayList<>();

        Object payload;

        Metadata metadata = Metadata.empty();

        protected Builder(Metadata metadata) {
            method(WebRequest.getMethod(metadata));
            url(WebRequest.getUrl(metadata));
            WebRequest.getHeaders(metadata).forEach((k, v) -> headers.put(k, new ArrayList<>(v)));
            headers(WebRequest.getHeaders(metadata));
            cookies.addAll(WebUtils.parseRequestCookieHeader(
                    Optional.ofNullable(headers.remove("Cookie")).orElseGet(List::of)
                            .stream().findFirst().orElse(null)));
        }

        protected Builder(WebRequest request) {
            method(request.getMethod());
            url(request.getPath());
            payload(request.getPayload());
            headers(request.getHeaders());
            cookies.addAll(WebUtils.parseRequestCookieHeader(
                    Optional.ofNullable(headers.remove("Cookie")).orElseGet(List::of)
                            .stream().findFirst().orElse(null)));
            metadata = request.getMetadata();
        }

        public Builder headerIfAbsent(String key, String value) {
            List<String> values = headers.computeIfAbsent(key, k -> new ArrayList<>());
            if (values.isEmpty()) {
                values.add(value);
            }
            return this;
        }

        public Builder header(String key, String value) {
            return header(key, value, false);
        }

        public Builder header(String key, String value, boolean ifAbsent) {
            List<String> values = headers.computeIfAbsent(key, k -> new ArrayList<>());
            if (values.isEmpty() || !ifAbsent) {
                values.add(value);
            }
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder clearHeader(String key) {
            headers.computeIfPresent(key, (k, v) -> null);
            return this;
        }

        public Builder cookie(HttpCookie cookie) {
            cookies.add(cookie);
            return this;
        }

        public Builder contentType(String contentType) {
            return header("Content-Type", contentType);
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Map<String, List<String>> headers() {
            var result = WebUtils.asHeaderMap(headers);
            if (acceptGzipEncoding) {
                result.computeIfAbsent("Accept-Encoding", k -> List.of("gzip"));
            }
            if (!result.containsKey("Content-Type")) {
                if (payload instanceof String) {
                    result.put("Content-Type", List.of("text/plain"));
                } else if (payload instanceof byte[]) {
                    result.put("Content-Type", List.of("application/octet-stream"));
                }
            }
            if (!cookies.isEmpty()) {
                result.put("Cookie", List.of(cookies.stream().map(WebUtils::toRequestHeaderString)
                                                     .collect(Collectors.joining("; "))));
            }
            return result;
        }

        public WebRequest build() {
            if (method == null) {
                throw new IllegalStateException("HTTP request method not set");
            }
            return new WebRequest(this);
        }
    }
}
