package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.Value;

import java.util.Set;

@Value
public class RepairRelationships extends Command {
    String aggregateId;
    String aggregateType;
    Set<String> entityIds;
    Guarantee guarantee;
}
