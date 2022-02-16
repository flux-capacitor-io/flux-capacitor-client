package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.api.QueryResult;
import lombok.Value;

import java.util.Map;

@Value
public class GetAggregateIdsResult implements QueryResult {
    long requestId;
    Map<String, String> aggregateIds;
    long timestamp = System.currentTimeMillis();
}