package io.fluxcapacitor.javaclient.web;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

@Value
public class WebParameters {
    @Getter(AccessLevel.NONE)
    String value;
    HttpRequestMethod method;

    public String getPath() {
        return value.startsWith("/") ? value : "/" + value;
    }
}
