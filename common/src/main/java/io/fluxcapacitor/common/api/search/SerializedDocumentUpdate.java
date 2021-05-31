package io.fluxcapacitor.common.api.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder")
public class SerializedDocumentUpdate {
    BulkUpdate.Type type;
    String id;
    String collection;
    SerializedDocument object;
}
