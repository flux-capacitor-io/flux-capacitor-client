package io.fluxcapacitor.common.api.modeling;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Relationship {
    String aggregateId;
    String aggregateType;
    String entityId;
}
