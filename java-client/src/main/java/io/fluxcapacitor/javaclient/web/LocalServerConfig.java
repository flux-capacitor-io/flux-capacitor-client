package io.fluxcapacitor.javaclient.web;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class LocalServerConfig {
    @NonNull Integer port;
    @Default boolean ignore404 = true;
}
