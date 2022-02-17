package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.api.Request;
import lombok.Value;

@Value
public class GetAggregateIds extends Request {
    String entityId;
}
