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

import io.fluxcapacitor.common.LazyInputStream;
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
import org.springframework.util.function.ThrowingSupplier;

import java.beans.ConstructorProperties;
import java.io.InputStream;
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

    @NonNull
    Map<String, List<String>> headers;
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

    /**
     * Constructs a new WebResponse instance using the provided Message object.
     *
     * @param m the {@code Message} object from which the payload, metadata, message ID, and timestamp are extracted
     *          to initialize the WebResponse.
     */
    public WebResponse(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    public static WebResponse ok(Object payload, Map<String, String> headers) {
        return builder().status(200).payload(payload).singleValuedHeaders(headers).build();
    }

    public static WebResponse ok(ThrowingSupplier<? extends InputStream> inputStreamSupplier,
                                 Map<String, String> headers) {
        return ok(new LazyInputStream(inputStreamSupplier), headers);
    }

    public static WebResponse partial(ThrowingSupplier<? extends InputStream> inputStreamSupplier, Map<String, String> headers) {
        return builder().status(206).payload(new LazyInputStream(inputStreamSupplier)).singleValuedHeaders(headers).build();
    }

    public static WebResponse notModified(Map<String, String> headers) {
        return builder().status(304).singleValuedHeaders(headers).build();
    }

    public static WebResponse notFound(String reason) {
        return builder().status(404).payload(reason).build();
    }

    /**
     * Serializes the response using the content type if applicable.
     */
    @Override
    public SerializedMessage serialize(Serializer serializer) {
        return headers.getOrDefault("Content-Type", List.of()).stream().findFirst().map(
                        format -> new SerializedMessage(serializer.serialize(getEncodedPayload(), format), getMetadata(),
                                                        getMessageId(), getTimestamp().toEpochMilli()))
                .orElseGet(() -> super.serialize(serializer));
    }

    /**
     * Constructs a Metadata object containing the provided status code and headers.
     *
     * @param statusCode the HTTP status code to be included in the metadata
     * @param headers a map of HTTP headers where each key is a header name and the corresponding
     *                value is a list of header values
     * @return a Metadata object containing the status code and headers
     */
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

    /**
     * Converts this WebResponse instance into a builder, which can be used to create a modified copy of the instance.
     *
     * @return a new {@code WebResponse.Builder} initialized with the properties of the current WebResponse instance.
     */
    public WebResponse.Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new {@link WebResponse.Builder} instance for constructing {@link WebResponse} objects.
     *
     * @return a new instance of the {@link WebResponse.Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves the headers from the provided metadata.
     *
     * @param metadata the metadata object from which headers should be retrieved
     * @return a map containing header names as keys and a list of corresponding header values. If no headers are found,
     * an empty map is returned.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
    }

    /**
     * Retrieves the status code from the provided metadata.
     *
     * @param metadata the metadata containing information associated with the response
     * @return the status code as an Integer if present; otherwise, null
     */
    public static Integer getStatusCode(Metadata metadata) {
        return Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
    }

    /**
     * Retrieves the decoded payload from the response and casts it to the specified type.
     *
     * @param <R> The type to which the payload will be cast.
     * @return The decoded payload of the response, cast to the specified type.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <R> R getPayload() {
        return (R) getDecodedPayload();
    }

    /**
     * Retrieves the payload and converts it to the specified type.
     *
     * @param <R>  the desired type of the returned payload
     * @param type the target type to which the payload should be converted
     * @return the payload converted to the specified type R
     */
    @Override
    public <R> R getPayloadAs(Type type) {
        return JSON_FORMAT.equalsIgnoreCase(getContentType())
                ? JsonUtils.convertValue(getPayload(), type)
                : super.getPayloadAs(type);
    }

    /**
     * Retrieves the list of header values associated with the given header name. If no headers are found for the
     * provided name, an empty list is returned.
     *
     * @param name the name of the header to retrieve
     * @return a list of header values associated with the specified name, or an empty list if none are found
     */
    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name, Collections.emptyList());
    }

    /**
     * Retains only the specified headers from the current WebResponse. Any headers not listed in the provided header
     * names will be removed.
     *
     * @param headerNames the names of headers to be retained in the WebResponse
     * @return a new WebResponse instance containing only the specified headers
     */
    public WebResponse retainHeaders(String... headerNames) {
        var filtered = WebUtils.asHeaderMap(headers);
        filtered.keySet().retainAll(Arrays.asList(headerNames));
        return toBuilder().clearHeaders().headers(filtered).build();
    }

    /**
     * Retrieves the value of the first occurrence of the specified header name.
     *
     * @param name the name of the HTTP header to retrieve
     * @return the first value associated with the specified header name, or null if the header is not present
     */
    public String getHeader(String name) {
        return getHeaders(name).stream().findFirst().orElse(null);
    }

    /**
     * Retrieves a list of cookies from the response's "Set-Cookie" headers. The cookies are parsed from the headers
     * using the {@code WebUtils.parseResponseCookieHeader} method.
     *
     * @return a list of {@code HttpCookie} objects parsed from the "Set-Cookie" headers; an empty list if there are no
     * cookies or the headers are absent
     */
    public List<HttpCookie> getCookies() {
        return WebUtils.parseResponseCookieHeader(getHeaders("Set-Cookie"));
    }

    /**
     * Retrieves the value of the "Content-Type" header from the HTTP response. If the header is not present, returns
     * null.
     *
     * @return the value of the "Content-Type" header, or null if the header is absent
     */
    public String getContentType() {
        return getHeader("Content-Type");
    }

    /**
     * Returns all HTTP headers associated with the current response.
     */
    public @NonNull Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Returns the HTTP status code associated with the current response.
     */
    public Integer getStatus() {
        return status;
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

    /**
     * Fluent builder for {@link WebResponse}. Use {@link #builder()} to start building.
     */
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
                if (payload instanceof byte[] || payload instanceof InputStream) {
                    return contentType("application/octet-stream");
                }
            }
            return this;
        }

        public Builder singleValuedHeaders(Map<String, String> headers) {
            headers.forEach(this::header);
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
