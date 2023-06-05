package io.fluxcapacitor.javaclient.modeling;

import lombok.Value;

@Value
public class SearchParameters {
    boolean searchable;
    String collection;
    String timestampPath;
}
