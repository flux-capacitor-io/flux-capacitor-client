package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.beans.ConstructorProperties;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.ANY;
import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebRequest extends Message {
    private static final Map<Annotation, Predicate<DeserializingMessage>> filterCache = new ConcurrentHashMap<>();

    public static Builder builder() {
        return new Builder();
    }

    public static BiPredicate<DeserializingMessage, Annotation> getWebRequestFilter() {
        return (message, annotation) -> filterCache.computeIfAbsent(annotation, a -> {
            Annotation typeAnnotation = getTypeAnnotation(a.annotationType(), HandleWeb.class);
            Predicate<String> pathTest = ReflectionUtils.<String>readProperty("value", a)
                    .map(path -> path.startsWith("/") ? path : "/" + path)
                    .map(SearchUtils::convertGlobToRegex).map(Pattern::asMatchPredicate)
                    .<Predicate<String>>map(p -> s -> p.test(s.startsWith("/") ? s : "/" + s))
                    .orElse(p -> true);
            Predicate<String> methodTest = ReflectionUtils.<HttpRequestMethod>readProperty("method", a)
                    .or(() -> ReflectionUtils.readProperty("method", typeAnnotation))
                    .<Predicate<String>>map(r -> r == ANY ? p -> true : p -> r.name().equals(p))
                    .orElse(p -> true);
            return msg -> {
                String path = requireNonNull(msg.getMetadata().get("path"),
                                             "Web request path is missing in the metadata of a WebRequest message");
                String method = requireNonNull(msg.getMetadata().get("method"),
                                               "Web request method is missing in the metadata of a WebRequest message");
                return pathTest.test(path) && methodTest.test(method);
            };
        }).test(message);
    }

    @NonNull String path;
    @NonNull HttpRequestMethod method;
    @NonNull Map<String, List<String>> headers;

    private WebRequest(Builder builder) {
        super(builder.payload(), Metadata.of("path", builder.path(), "method", builder.method().name(),
                                             "headers", builder.headers()));
        this.path = builder.path();
        this.method = builder.method();
        this.headers = builder.headers();
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    WebRequest(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
        this.path = getPath(metadata);
        this.method = getMethod(metadata);
        this.headers = getHeaders(metadata);
    }

    public WebRequest(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public SerializedMessage serialize(Serializer serializer) {
        return headers.getOrDefault("Content-Type", List.of()).stream().findFirst().map(
                        format -> new SerializedMessage(serializer.serialize(getPayload(), format), getMetadata(),
                                                        getMessageId(), getTimestamp().toEpochMilli()))
                .orElseGet(() -> super.serialize(serializer));
    }

    @Override
    public WebRequest withMetadata(Metadata metadata) {
        return new WebRequest(super.withMetadata(metadata));
    }

    @Override
    public WebRequest withPayload(Object payload) {
        return new WebRequest(super.withPayload(payload));
    }

    public static String getPath(Metadata metadata) {
        return Optional.ofNullable(metadata.get("path")).map(p -> p.startsWith("/") ? p : "/" + p)
                .orElseThrow(() -> new IllegalStateException("WebRequest is malformed: path is missing"));
    }

    public static HttpRequestMethod getMethod(Metadata metadata) {
        return Optional.ofNullable(metadata.get("method")).map(m -> {
            try {
                return HttpRequestMethod.valueOf(m);
            } catch (Exception e) {
                throw new IllegalStateException("WebRequest is malformed: unrecognized http method");
            }
        }).orElseThrow(() -> new IllegalStateException("WebRequest is malformed: http method is missing"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        String path;
        HttpRequestMethod method;
        @Setter(AccessLevel.NONE)
        Map<String, List<String>> headers = new HashMap<>();
        Object payload;

        public Builder header(String key, String value) {
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder contentType(String contentType) {
            return header("Content-Type", contentType);
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

        public WebRequest build() {
            return new WebRequest(this);
        }
    }
}
