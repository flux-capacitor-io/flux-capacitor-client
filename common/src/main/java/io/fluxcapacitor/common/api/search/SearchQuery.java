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
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;

import static io.fluxcapacitor.common.api.search.constraints.AllConstraint.all;
import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;

@Value
@Builder(toBuilder = true, builderClassName = "Builder")
public class SearchQuery implements Constraint {
    String collection;
    Instant from, until;
    @Singular List<Constraint> constraints;

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean matches(Document d) {
        if (!constraints.stream().allMatch(c -> c.matches(d))) {
            return false;
        }
        if (from != null && d.getTimestamp().compareTo(from) >= 0) {
            return false;
        }
        if (until != null && d.getTimestamp().compareTo(until) <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public SearchQuery and(Constraint other) {
        return (other instanceof AllConstraint
                ? toBuilder().constraints(((AllConstraint) other).getAll()) : toBuilder().constraint(other)).build();
    }

    @Override
    public SearchQuery or(Constraint other) {
        Constraint[] constraints = this.constraints.toArray(new Constraint[0]);
        return this.constraints.isEmpty() ? toBuilder().constraint(other).build() :
                toBuilder().clearConstraints().constraint(any(all(constraints), other)).build();
    }
}
