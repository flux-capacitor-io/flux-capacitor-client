package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.beans.ConstructorProperties;
import java.lang.reflect.Executable;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.ANY;
import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebRequest extends Message {
    private static final Map<Executable, Predicate<HasMessage>> filterCache = new ConcurrentHashMap<>();

    public static Builder builder() {
        return new Builder();
    }

    public static MessageFilter<HasMessage> getWebRequestFilter() {
        return (message, executable) -> filterCache.computeIfAbsent(executable, e -> {
            var handleWeb = getAnnotationAs(e, HandleWeb.class, HandleWebParams.class).orElseThrow();
            Predicate<String> pathTest = Optional.of(handleWeb.getValue())
                    .map(url -> url.startsWith("/") ? url : "/" + url)
                    .map(SearchUtils::convertGlobToRegex).map(Pattern::asMatchPredicate)
                    .<Predicate<String>>map(p -> s -> p.test(s.startsWith("/") ? s : "/" + s))
                    .orElse(p -> true);
            Predicate<String> methodTest = Optional.of(handleWeb.getMethod())
                    .<Predicate<String>>map(r -> r == ANY ? p -> true : p -> r.name().equals(p))
                    .orElse(p -> true);
            return msg -> {
                String path = requireNonNull(msg.getMetadata().get("url"),
                                             "Web request url is missing in the metadata of a WebRequest message");
                String method = requireNonNull(msg.getMetadata().get("method"),
                                               "Web request method is missing in the metadata of a WebRequest message");
                return pathTest.test(path) && methodTest.test(method);
            };
        }).test(message);
    }

    @Value
    protected static class HandleWebParams {
        String value;
        HttpRequestMethod method;
    }

    @NonNull String path;
    @NonNull HttpRequestMethod method;
    @NonNull Map<String, List<String>> headers;

    @Getter(lazy = true)
    @JsonIgnore
    List<HttpCookie> cookies = Optional.ofNullable(getHeader("Cookie")).map(HttpCookie::parse).orElse(Collections.emptyList());

    private WebRequest(Builder builder) {
        super(builder.payload(), Metadata.of("url", builder.url(), "method", builder.method().name(),
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
    public WebRequest withPayload(Object payload) {
        return new WebRequest(super.withPayload(payload));
    }

    public String getHeader(String name) {
        return getHeaders(name).stream().findFirst().orElse(null);
    }

    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name, Collections.emptyList());
    }

    public Optional<HttpCookie> getCookie(String name) {
        return getCookies().stream().filter(c -> Objects.equals(name, c.getName())).findFirst();
    }

    public static String getUrl(Metadata metadata) {
        return Optional.ofNullable(metadata.get("url")).map(u -> u.startsWith("/") ? u : "/" + u)
                .orElseThrow(() -> new IllegalStateException("WebRequest is malformed: url is missing"));
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

    public static Optional<HttpCookie> getCookie(Metadata metadata, String name) {
        return getHeaders(metadata).getOrDefault("Cookie", Collections.emptyList()).stream().findFirst()
                .flatMap(h -> HttpCookie.parse(h).stream().filter(c -> Objects.equals(name, c.getName())).findFirst());
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        String url;
        HttpRequestMethod method;
        Map<String, List<String>> headers = new HashMap<>();

        @Setter(AccessLevel.NONE)
        List<HttpCookie> cookies = new ArrayList<>();

        Object payload;

        public Builder header(String key, String value) {
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
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

        public Map<String, List<String>> headers() {
            var result = headers;
            if (!cookies.isEmpty()) {
                result = new HashMap<>(headers);
                result.put("Cookie", List.of(cookies.stream().map(HttpCookie::toString).collect(Collectors.joining("; "))));
            }
            return result;
        }

        public WebRequest build() {
            return new WebRequest(this);
        }
    }
}
