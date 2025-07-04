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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.web.internal.WebUtilsInternal;
import io.jooby.Body;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.DefaultContext;
import io.jooby.Formdata;
import io.jooby.MediaType;
import io.jooby.ParamSource;
import io.jooby.QueryString;
import io.jooby.Route;
import io.jooby.Router;
import io.jooby.Sender;
import io.jooby.ServerSentEmitter;
import io.jooby.StatusCode;
import io.jooby.ValueConverter;
import io.jooby.ValueNode;
import io.jooby.WebSocket;
import io.jooby.buffer.DataBuffer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Default implementation of {@link WebRequestContext} that adapts a {@link DeserializingMessage} of type
 * {@link io.fluxcapacitor.common.MessageType#WEBREQUEST} to a Jooby-compatible {@link Context}.
 * <p>
 * This context serves as the main bridge between Flux Capacitor’s web messaging infrastructure and
 * the Jooby framework’s routing, parameter resolution, and content access APIs.
 * It enables handler matching and invocation based on path, query, form, header, and cookie parameters.
 *
 * <h2>Purpose</h2>
 * {@code DefaultWebRequestContext} wraps incoming web request metadata into an object that behaves
 * like a live HTTP request. It can be used during handler method resolution to extract parameter values,
 * match URI paths, handle routing with method + origin specificity, and access request body, headers, etc.
 *
 * <h2>Internals</h2>
 * <ul>
 *   <li>Backed by a {@link DeserializingMessage} representing a {@code WEBREQUEST}</li>
 *   <li>Implements Jooby’s {@link DefaultContext} to take advantage of its form/query/header abstractions</li>
 *   <li>Supports delayed body deserialization using a {@code Supplier<byte[]>}</li>
 *   <li>Provides metadata-based implementations of headers, cookies, and remote address resolution</li>
 *   <li>Can be accessed statically from {@link #getCurrentWebRequestContext()}</li>
 * </ul>
 *
 * <h2>Usage in Routing</h2>
 * Used primarily by {@link io.fluxcapacitor.javaclient.web.WebHandlerMatcher}, this context provides the
 * foundation for matching {@link io.fluxcapacitor.javaclient.web.WebPattern}s and routing to appropriate
 * handler methods.
 *
 * <h2>Extensibility</h2>
 * While this implementation currently delegates most parsing and matching to Jooby, the abstraction is intentionally
 * based on the {@link WebRequestContext} interface to allow future replacement of Jooby with another HTTP framework
 * without affecting the rest of the dispatching system.
 *
 * <p>
 * Many Jooby methods are stubbed or unsupported, as Flux only uses a subset of Jooby's APIs for request introspection
 * (not response handling).
 *
 * @see WebRequestContext
 * @see io.fluxcapacitor.javaclient.web.WebHandlerMatcher
 * @see io.fluxcapacitor.javaclient.web.WebRequest
 * @see io.fluxcapacitor.javaclient.web.WebPattern
 */
@Value
@AllArgsConstructor
public class DefaultWebRequestContext implements DefaultContext, WebRequestContext {

    /**
     * Returns the current {@link DefaultWebRequestContext} from the active {@link DeserializingMessage},
     * or {@code null} if no such message is available or it is not a {@link MessageType#WEBREQUEST}.
     * <p>
     * This method provides a convenient way to access the web request context during handler invocation,
     * filtering, or logging without needing to explicitly pass it around.
     *
     * @return the current {@code DefaultWebRequestContext}, or {@code null} if unavailable
     *
     * @see DeserializingMessage#getCurrent()
     * @see #getWebRequestContext(DeserializingMessage)
     */
    public static DefaultWebRequestContext getCurrentWebRequestContext() {
        return Optional.ofNullable(DeserializingMessage.getCurrent()).map(
                DefaultWebRequestContext::getWebRequestContext).orElse(null);
    }

    /**
     * Creates or retrieves a {@link DefaultWebRequestContext} from the given {@link DeserializingMessage}.
     * <p>
     * Internally caches the context in the message so that repeated lookups are efficient.
     * This method should only be used with messages of type {@link MessageType#WEBREQUEST}; otherwise,
     * an {@link IllegalArgumentException} is thrown.
     *
     * @param message a deserialized {@code WEBREQUEST} message
     * @return a {@code DefaultWebRequestContext} wrapping the request metadata and body
     * @throws IllegalArgumentException if the message type is not {@code WEBREQUEST}
     *
     * @see DeserializingMessage#computeContextIfAbsent(Class, java.util.function.Function)
     * @see #getCurrentWebRequestContext()
     */
    public static DefaultWebRequestContext getWebRequestContext(DeserializingMessage message) {
        return message.computeContextIfAbsent(DefaultWebRequestContext.class, DefaultWebRequestContext::new);
    }

    private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F.:]+");
    private static final Router ROUTER = new ConvertingRouter();

    Supplier<byte[]> bodySupplier;
    Metadata metadata;

    @Getter(lazy = true)
    URI uri = URI.create(WebRequest.getUrl(metadata));
    @With
    String method;
    @Getter(lazy = true)
    String requestPath = getUri().getRawPath();
    @Getter(lazy = true)
    @jakarta.annotation.Nullable
    String origin = Optional.ofNullable(getUri().getScheme())
            .map(scheme -> scheme + "://" + getUri().getHost() + Optional.ofNullable(getUri().getPort())
                    .filter(p -> p >= 0).map(p -> ":" + p).orElse("")).orElse(null);
    @Getter(lazy = true)
    @Accessors(fluent = true)
    QueryString query = QueryString.create(this, getUri().getQuery());
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Getter(lazy = true)
    @Accessors(fluent = true)
    ValueNode header = io.jooby.Value.headers(this, (Map) WebRequest.getHeaders(metadata));
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Map<String, String> cookieMap = WebRequest.getHeaders(metadata).getOrDefault("Cookie", Collections.emptyList())
            .stream().findFirst().map(WebUtils::parseRequestCookieHeader).orElseGet(Collections::emptyList)
            .stream().collect(toMap(HttpCookie::getName, HttpCookie::getValue));
    @Getter(lazy = true)
    Map<String, Object> attributes = new LinkedHashMap<>();
    @Getter(lazy = true)
    String remoteAddress = Stream.of("X-Forwarded-For", "Forwarded", "X-Real-IP")
            .flatMap(h -> WebRequest.getHeader(metadata, h).stream())
            .flatMap(s -> {
                Matcher matcher = IP_PATTERN.matcher(s);
                return matcher.find() ? Stream.of(matcher.group()) : Stream.empty();
            })
            .findFirst()
            .orElse("");
    @NonFinal
    @Setter
    @Accessors(chain = true)
    Map<String, String> pathMap = new LinkedHashMap<>();
    @NonFinal
    @Setter
    @Accessors(chain = true)
    Route route;
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Body body = Body.of(this, bodySupplier.get());
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Formdata form = Formdata.create(this);

    DefaultWebRequestContext(DeserializingMessage message) {
        if (message.getMessageType() != MessageType.WEBREQUEST) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        bodySupplier = () -> message.getSerializedObject().getData().getValue();
        metadata = message.getMetadata();
        method = WebRequest.getMethod(metadata);
    }

    @NotNull
    @Override
    public Map<String, String> pathMap() {
        return pathMap;
    }

    @Override
    public ParameterValue getParameter(String name, WebParameterSource... sources) {
        var value = lookup(name, Arrays.stream(sources).map(type -> switch (type) {
            case PATH -> ParamSource.PATH;
            case HEADER -> ParamSource.HEADER;
            case COOKIE -> ParamSource.COOKIE;
            case FORM -> ParamSource.FORM;
            case QUERY -> ParamSource.QUERY;
        }).toArray(ParamSource[]::new));
        return new ParameterValue(value);
    }

    /*
        Below methods are not *yet* supported but should in the future.
     */

    @NotNull
    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public List<Certificate> getClientCertificates() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getScheme() {
        throw new UnsupportedOperationException();
    }

    /*
        Below methods should never be invoked as they won't be exposed in Flux apps
     */

    @NotNull
    @Override
    public Context setRemoteAddress(@NotNull String remoteAddress) {
        return this;
    }

    @NotNull
    @Override
    public Context setHost(@NotNull String host) {
        return this;
    }

    @NotNull
    @Override
    public Context setPort(int port) {
        return this;
    }

    @NotNull
    @Override
    public Context setScheme(@NotNull String scheme) {
        return this;
    }

    @Override
    public boolean isInIoThread() {
        return false;
    }

    @NotNull
    @Override
    public Context dispatch(@NotNull Runnable action) {
        return this;
    }

    @NotNull
    @Override
    public Context dispatch(@NotNull Executor executor, @NotNull Runnable action) {
        return this;
    }

    @NotNull
    @Override
    public Context detach(@NotNull Route.Handler next) throws Exception {
        return this;
    }

    @NotNull
    @Override
    public Context upgrade(@NotNull WebSocket.Initializer handler) {
        return this;
    }

    @NotNull
    @Override
    public Context upgrade(@NotNull ServerSentEmitter.Handler handler) {
        return this;
    }

    @NotNull
    @Override
    public Context setResponseHeader(@NotNull String name, @NotNull String value) {
        return this;
    }

    @NotNull
    @Override
    public Context removeResponseHeader(@NotNull String name) {
        return this;
    }

    @NotNull
    @Override
    public Context removeResponseHeaders() {
        return this;
    }

    @NotNull
    @Override
    public Context setResponseLength(long length) {
        return this;
    }

    @Nullable
    @Override
    public String getResponseHeader(@NotNull String name) {
        return null;
    }

    @Override
    public long getResponseLength() {
        return -1L;
    }

    @Override
    public boolean isResponseStarted() {
        return false;
    }

    @Override
    public boolean getResetHeadersOnError() {
        return false;
    }

    @NotNull
    @Override
    public MediaType getResponseType() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Context setResponseCode(int statusCode) {
        return this;
    }

    @NotNull
    @Override
    public StatusCode getResponseCode() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public OutputStream responseStream() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Sender responseSender() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public PrintWriter responseWriter(@NotNull MediaType contentType, @Nullable Charset charset) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Router getRouter() {
        return ROUTER;
    }

    @NotNull
    @Override
    public Context setMethod(@NotNull String method) {
        return this;
    }

    @NotNull
    @Override
    public Context setRequestPath(@NotNull String path) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull String data, @NotNull Charset charset) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull byte[] data) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull ByteBuffer data) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull DataBuffer data) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull ByteBuffer[] data) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull ReadableByteChannel channel) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull InputStream input) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull FileChannel file) {
        return this;
    }

    @NotNull
    @Override
    public Context send(@NotNull StatusCode statusCode) {
        return this;
    }

    @NotNull
    @Override
    public Context setResetHeadersOnError(boolean value) {
        return this;
    }

    @NotNull
    @Override
    public Context setResponseCookie(@NotNull Cookie cookie) {
        return this;
    }

    @NotNull
    @Override
    public Context setResponseType(@NotNull String contentType) {
        return this;
    }

    @NotNull
    @Override
    public Context setResponseType(@NotNull MediaType contentType, @Nullable Charset charset) {
        return this;
    }

    @NotNull
    @Override
    public Context setDefaultResponseType(@NotNull MediaType contentType) {
        return this;
    }

    @NotNull
    @Override
    public Context onComplete(@NotNull Route.Complete task) {
        return this;
    }

    public boolean matchesAny(Collection<String> urlPatterns) {
        return urlPatterns.stream().anyMatch(this::matches);
    }

    protected static class ConvertingRouter implements Router {
        @Delegate
        private final Router delegate = WebUtilsInternal.router();

        public ConvertingRouter() {
            delegate.converter(new DefaultConverter());
        }
    }

    protected static class DefaultConverter implements ValueConverter<io.jooby.Value> {
        @Override
        public boolean supports(@NotNull Class type) {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object convert(@NotNull io.jooby.Value value, @NotNull Class type) {
            return new Wrapper<>(value.valueOrNull()).get(type);
        }
    }

    @Value
    protected static class Wrapper<T> {
        T value;

        <V> V get(Class<V> type) {
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            Wrapper<V> result = JsonUtils.convertValue(this, tf -> tf.constructParametricType(Wrapper.class, type));
            return result.value;
        }
    }
}
