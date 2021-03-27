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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.search.constraints.AllConstraint;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.search.Document;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(@JsonSubTypes.Type(MatchConstraint.class))
public interface Constraint {
    Constraint noOp = doc -> true;

    boolean matches(Document document);

    default Constraint and(Constraint other) {
        List<Constraint> constraints = new ArrayList<>();
        if (this instanceof AllConstraint) {
            constraints.addAll(((AllConstraint) this).getAll());
        } else {
            constraints.add(this);
        }
        if (other instanceof AllConstraint) {
            constraints.addAll(((AllConstraint) other).getAll());
        } else {
            constraints.add(other);
        }
        return new AllConstraint(constraints);
    }

    default Constraint or(Constraint other) {
        List<Constraint> constraints = new ArrayList<>();
        if (this instanceof AnyConstraint) {
            constraints.addAll(((AnyConstraint) this).getAny());
        } else {
            constraints.add(this);
        }
        if (other instanceof AnyConstraint) {
            constraints.addAll(((AnyConstraint) other).getAny());
        } else {
            constraints.add(other);
        }
        return new AnyConstraint(constraints);
    }

}
