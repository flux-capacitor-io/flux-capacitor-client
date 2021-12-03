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

package io.fluxcapacitor.common.api.search.constraints;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
public class NotConstraint implements Constraint {
    public static NotConstraint not(Constraint constraint) {
        return new NotConstraint(constraint);
    }

    @NonNull Constraint not;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = new NotConstraint(getNot().decompose());

    @Override
    public boolean matches(Document document) {
        return !not.matches(document);
    }

    @Override
    public boolean hasPathConstraint() {
        return not.hasPathConstraint();
    }
}
