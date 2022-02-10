package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.api.QueryResult;
import lombok.Value;

import java.util.Set;

@Value
public class GetAggregateIdsResult implements QueryResult {
    long requestId;
    Set<String> aggregateIds;
    long timestamp = System.currentTimeMillis();
}