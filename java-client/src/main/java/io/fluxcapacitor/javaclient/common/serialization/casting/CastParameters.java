package io.fluxcapacitor.javaclient.common.serialization.casting;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class CastParameters {
    String type;
    int revision;
    int revisionDelta;
}
