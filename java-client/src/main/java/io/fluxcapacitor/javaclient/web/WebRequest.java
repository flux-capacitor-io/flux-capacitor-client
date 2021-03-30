package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.javaclient.web.WebUtils.headersJavaType;

public class WebRequest extends Message {

    public WebRequest(Object payload, String path, String method) {
        super(payload, Metadata.of("path", path, "method", method));
    }

    public WebRequest(Object payload, String path, String method, Map<String, List<String>> headers) {
        super(payload, Metadata.of("path", path, "method", method, "headers", headers));
    }

    public WebRequest(Object payload, Metadata metadata, String path, String method, Map<String, List<String>> headers) {
        super(payload, metadata.with("path", path, "method", method, "headers", headers));
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    public WebRequest(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
    }

    public <T extends Message> WebRequest(T m) {
        super(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
    }

    @Override
    public WebRequest withMetadata(Metadata metadata) {
        return new WebRequest(super.withMetadata(metadata));
    }

    @JsonIgnore
    public String getPath() {
        return getMetadata().get("path");
    }

    @JsonIgnore
    public String getMethod() {
        return getMetadata().get("method");
    }

    @JsonIgnore
    public Map<String, List<String>> getHeaders() {
        return getMetadata().get("headers", headersJavaType);
    }

}
