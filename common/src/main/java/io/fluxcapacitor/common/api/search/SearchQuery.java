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
    String collection;
    Instant since, before;
    @Singular List<Constraint> constraints;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decomposeConstraints = AllConstraint.all(constraints.stream().map(Constraint::decompose).collect(
            Collectors.toList()));

    @SuppressWarnings("RedundantIfStatement")
    public boolean matches(Document d) {
        if (!decomposeConstraints().matches(d)) {
            return false;
        }
        if (since != null && d.getTimestamp().compareTo(since) < 0) {
            return false;
        }
        if (before != null && d.getTimestamp().compareTo(before) >= 0) {
            return false;
        }
        return true;
    }
}
