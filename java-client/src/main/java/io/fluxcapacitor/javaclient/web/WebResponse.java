package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
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
    String statusText;

    private WebResponse(Builder builder) {
        super(builder.payload(), Metadata.of("status", builder.status(), "statusText", builder.statusText(), "headers",
                                             builder.headers()));
        this.status = builder.status();
        this.statusText = builder.statusText();
        this.headers = builder.headers();
    }

    @SuppressWarnings("unchecked")
    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    WebResponse(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
        this.headers = Optional.ofNullable(metadata.get("headers", Map.class)).orElse(Collections.emptyMap());
        this.status = Optional.ofNullable(metadata.get("status")).map(Integer::valueOf).orElse(null);
        this.statusText = metadata.get("statusText");
    }

    public WebResponse(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public WebResponse withMetadata(Metadata metadata) {
        return new WebResponse(super.withMetadata(metadata));
    }

    @Override
    public WebResponse withPayload(Object payload) {
        return new WebResponse(super.withPayload(payload));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Builder {
        Map<String, List<String>> headers = new HashMap<>();
        Object payload;
        Integer status;
        String statusText;

        public WebResponse build() {
            return new WebResponse(this);
        }
    }
}
