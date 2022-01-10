package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebResponse extends Message {

    public static Builder builder() {
        return new Builder();
    }

    @NonNull Map<String, List<String>> headers;
    Integer status;

    private WebResponse(Builder builder) {
        super(builder.payload(), Metadata.of("status", builder.status(), "headers", builder.headers()));
        this.status = builder.status();
        this.headers = builder.headers();
    }

    @SuppressWarnings("unchecked")
    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    WebResponse(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
        this.headers = Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
        this.status = Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
    }

    public WebResponse(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public SerializedMessage serialize(Serializer serializer) {
        return headers.getOrDefault("Content-Type", List.of()).stream().findFirst().map(
                        format -> new SerializedMessage(serializer.serialize(getPayload(), format), getMetadata(),
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
    public WebResponse withPayload(Object payload) {
        return new WebResponse(super.withPayload(payload));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
    }

    public static Integer getStatusCode(Metadata metadata) {
        return Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        Map<String, List<String>> headers = new HashMap<>();
        Object payload;
        Integer status;

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

        public Builder header(String key, String value) {
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder contentType(String contentType) {
            return header("Content-Type", contentType);
        }

        public WebResponse build() {
            return new WebResponse(this);
        }
    }
}