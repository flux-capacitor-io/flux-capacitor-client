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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.common.serialization.compression.CompressionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.tracking.handling.ResponseMapper;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.fluxcapacitor.common.api.Data.JSON_FORMAT;
import static io.fluxcapacitor.javaclient.web.WebUtils.asHeaderMap;
import static java.util.stream.Collectors.toList;

/**
 * Represents a response to a {@link WebRequest} in the Flux platform.
 * <p>
 * {@code WebResponse} extends {@link Message} and includes:
 * </p>
 * <ul>
 *   <li>{@code status} – HTTP status code (e.g. 200, 404)</li>
 *   <li>{@code headers} – HTTP headers</li>
 *   <li>{@code cookies} – parsed from {@code Set-Cookie}</li>
 *   <li>{@code contentType} – inferred from headers</li>
 * </ul>
 *
 * <p>
 * The response payload may be encoded and compressed (e.g. gzip) based on metadata.
 * It supports transformation, enrichment, and construction via a {@code Builder}.
 * </p>
 *
 * @see WebRequest
 * @see ResponseMapper
 * @see WebResponseMapper
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebResponse extends Message {
    private static final List<String> gzipEncoding = List.of("gzip");
    @NonNull Map<String, List<String>> headers;
    Integer status;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    Object decodedPayload = decodePayload();

    private WebResponse(Builder builder) {
        super(builder.payload(), Metadata.of("status", builder.status(), "headers", builder.headers()));
        this.status = builder.status();
        this.headers = builder.headers();
    }

    @SuppressWarnings("unchecked")
    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    WebResponse(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
        this.headers = Optional.ofNullable(metadata.get("headers", Map.class))
                .map(map -> asHeaderMap(map)).orElseGet(WebUtils::emptyHeaderMap);
        this.status = Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
    }

    public WebResponse(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public SerializedMessage serialize(Serializer serializer) {
        return headers.getOrDefault("Content-Type", List.of()).stream().findFirst().map(
                        format -> new SerializedMessage(serializer.serialize(getEncodedPayload(), format), getMetadata(),
                                                        getMessageId(), getTimestamp().toEpochMilli()))
                .orElseGet(() -> super.serialize(serializer));
    }

    public static Metadata asMetadata(int statusCode, Map<String, List<String>> headers) {
        return Metadata.of("status", statusCode, "headers", headers);
    }

    @Override
    public WebResponse withMetadata(Metadata metadata) {
        return new WebResponse(super.withMetadata(metadata));
    }

    @Override
    public WebResponse addMetadata(Metadata metadata) {
        return (WebResponse) super.addMetadata(metadata);
    }

    @Override
    public WebResponse addMetadata(String key, Object value) {
        return (WebResponse) super.addMetadata(key, value);
    }

    @Override
    public WebResponse addMetadata(Object... keyValues) {
        return (WebResponse) super.addMetadata(keyValues);
    }

    @Override
    public WebResponse addMetadata(Map<String, ?> values) {
        return (WebResponse) super.addMetadata(values);
    }

    @Override
    public WebResponse addUser(User user) {
        return (WebResponse) super.addUser(user);
    }

    @Override
    public WebResponse withPayload(Object payload) {
        if (payload == getPayload()) {
            return this;
        }
        return toBuilder().payload(payload).build();
    }

    @Override
    public WebResponse withMessageId(String messageId) {
        return new WebResponse(super.withMessageId(messageId));
    }

    @Override
    public WebResponse withTimestamp(Instant timestamp) {
        return new WebResponse(super.withTimestamp(timestamp));
    }

    public WebResponse.Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
    }

    public static Integer getStatusCode(Metadata metadata) {
        return Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R getPayload() {
        return (R) getDecodedPayload();
    }

    @Override
    public <R> R getPayloadAs(Type type) {
        return JSON_FORMAT.equalsIgnoreCase(getContentType())
                ? JsonUtils.convertValue(getPayload(), type)
                : super.getPayloadAs(type);
    }

    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name, Collections.emptyList());
    }

    public WebResponse retainHeaders(String... headerNames) {
        var filtered = WebUtils.asHeaderMap(headers);
        filtered.keySet().retainAll(Arrays.asList(headerNames));
        return toBuilder().clearHeaders().headers(filtered).build();
    }

    public String getHeader(String name) {
        return getHeaders(name).stream().findFirst().orElse(null);
    }

    public List<HttpCookie> getCookies() {
        return WebUtils.parseResponseCookieHeader(getHeaders("Set-Cookie"));
    }

    public String getContentType() {
        return getHeader("Content-Type");
    }

    Object getEncodedPayload() {
        return super.getPayload();
    }

    @SneakyThrows
    Object decodePayload() {
        Object result = getEncodedPayload();
        if (result instanceof byte[] bytes && Objects.equals(getHeaders("Content-Encoding"), gzipEncoding)) {
            return CompressionUtils.decompress(bytes, CompressionAlgorithm.GZIP);
        }
        return result;
    }

    @Data
    @NoArgsConstructor
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        final Map<String, List<String>> headers = WebUtils.emptyHeaderMap();
        @Setter(AccessLevel.NONE)
        List<HttpCookie> cookies = new ArrayList<>();
        Object payload;
        Integer status;

        protected Builder(WebResponse response) {
            payload(response.getEncodedPayload());
            status(response.getStatus());
            headers(response.getHeaders());
            cookies.addAll(WebUtils.parseResponseCookieHeader(headers.remove("Set-Cookie")));
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            if (!headers().containsKey("Content-Type")) {
                if (payload instanceof String) {
                    return contentType("text/plain");
                }
                if (payload instanceof byte[]) {
                    return contentType("application/octet-stream");
                }
            }
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder header(String key, Collection<String> values) {
            headers.computeIfAbsent(key, k -> new ArrayList<>()).addAll(values);
            return this;
        }

        public Builder header(String key, String value) {
            return header(key, List.of(value));
        }

        public Builder clearHeader(String key) {
            headers.computeIfPresent(key, (k, v) -> null);
            return this;
        }

        public Builder clearHeaders() {
            headers.clear();
            return this;
        }

        public Builder cookie(HttpCookie cookie) {
            cookies.add(cookie);
            return this;
        }

        public Builder contentType(String contentType) {
            return header("Content-Type", contentType);
        }

        public Map<String, List<String>> headers() {
            if (!cookies.isEmpty()) {
                clearHeader("Set-Cookie").header(
                        "Set-Cookie", cookies.stream().map(WebUtils::toResponseHeaderString).collect(toList()));
            }
            return headers;
        }

        public Integer status() {
            return status == null ? (payload == null ? 204 : 200) : status;
        }

        public WebResponse build() {
            return new WebResponse(this);
        }
    }
}
