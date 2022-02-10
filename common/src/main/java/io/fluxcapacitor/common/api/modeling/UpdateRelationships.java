package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Request;
import lombok.Value;

import java.util.List;

@Value
public class UpdateRelationships extends Request {
    List<Relationship> dissociations;
    List<Relationship> associations;
    Guarantee guarantee;
}
