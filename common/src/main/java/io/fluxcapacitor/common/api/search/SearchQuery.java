/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fluxcapacitor.common.api.search.constraints.AllConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true, builderClassName = "Builder")
public class SearchQuery {
    @JsonProperty("collection")
    @Singular List<String> collections;
    Instant since, before;
    boolean requireTimestamp;
    @Singular List<Constraint> constraints;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decomposeConstraints = AllConstraint.all(getConstraints().stream().map(Constraint::decompose).collect(
            Collectors.toList()));

    @SuppressWarnings("RedundantIfStatement")
    public boolean matches(Document d) {
        if (!decomposeConstraints().matches(d)) {
            return false;
        }
        var documentStart = d.getTimestamp() == null ? d.getEnd() : d.getTimestamp();
        var documentEnd = d.getEnd() == null ? d.getTimestamp() : d.getEnd();
        if (requireTimestamp && documentStart == null) {
            return false;
        }
        if (since != null && documentEnd != null && documentEnd.compareTo(since) < 0) {
            return false;
        }
        if (before != null && documentStart != null && documentStart.compareTo(before) >= 0) {
            return false;
        }
        if (!collections.isEmpty() && !collections.contains(d.getCollection())) {
            return false;
        }
        return true;
    }
}
