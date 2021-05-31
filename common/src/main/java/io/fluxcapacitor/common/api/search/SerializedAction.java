package io.fluxcapacitor.common.api.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder")
public class SerializedAction {
    Action.Type type;
    String id;
    String collection;
    SerializedDocument object;
}
