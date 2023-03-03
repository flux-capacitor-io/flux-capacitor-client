package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.api.QueryResult;
import lombok.Value;

import java.util.List;

@Value
public class GetRelationshipsResult implements QueryResult {
    long requestId;
    List<Relationship> relationships;
    long timestamp = System.currentTimeMillis();
}