package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.javaclient.web.WebUtils.headersJavaType;
import static java.util.Collections.emptyMap;

public class WebRequest extends Message {

    public WebRequest(Object payload, String path, WebMethod method) {
        super(payload, Metadata.of("path", path, "method", method));
    }

    public WebRequest(Object payload, String path, WebMethod method, Map<String, List<String>> headers) {
        super(payload, Metadata.of("path", path, "method", method, "headers", headers));
    }

    public WebRequest(Object payload, Metadata metadata, String path, WebMethod method, Map<String, List<String>> headers) {
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
        return getPath(getMetadata());
    }

    @JsonIgnore
    public WebMethod getMethod() {
        return getMethod(getMetadata());
    }

    @JsonIgnore
    public Map<String, List<String>> getHeaders() {
        return getHeaders(getMetadata());
    }

    public static String getPath(Metadata metadata) {
        return metadata.get("path");
    }

    public static WebMethod getMethod(Metadata metadata) {
        try {
            return WebMethod.valueOf(metadata.get("method"));
        } catch (Exception e){
            return WebMethod.UNKNOWN;
        }
    }

    public static Map<String, List<String>> getHeaders(Metadata metadata) {
        return metadata.containsKey("headers") ? metadata.get("headers", headersJavaType) : emptyMap();
    }

}
