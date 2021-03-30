package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class WebResponse extends Message {

    public WebResponse(Object payload, int status) {
        super(payload, Metadata.of("status", status));
    }

    public WebResponse(Object payload, int status, Map<String, List<String>> headers) {
        super(payload, Metadata.of("status", status, "headers", headers));
    }

    public WebResponse(Object payload, Metadata metadata, int status, Map<String, List<String>> headers) {
        super(payload, metadata.with("status", status, "headers", headers));
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    public WebResponse(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        super(payload, metadata, messageId, timestamp);
    }
}
