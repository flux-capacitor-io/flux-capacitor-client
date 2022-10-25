package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Request;
import lombok.Value;

import java.util.Set;

@Value
public class UpdateRelationships extends Request {
    Set<Relationship> associations;
    Set<Relationship> dissociations;
    Guarantee guarantee;
}
