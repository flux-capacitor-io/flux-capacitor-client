package io.fluxcapacitor.common.api.modeling;

import lombok.Value;

@Value
public class Relationship {
    String aggregateId;
    String aggregateType;
    String entityId;
}
