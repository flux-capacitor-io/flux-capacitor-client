/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link Constraint} that checks whether one or more paths exist in a document.
 * <p>
 * This constraint matches any document that contains a non-null value at the specified path(s).
 * <p>
 * If multiple paths are provided, the constraint will match if <em>any</em> of them are present (i.e. this behaves as
 * an {@link AnyConstraint}).
 * <p>
 * Optionally, sub-paths can be included using a glob-style match with {@code /**}.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Matches documents where "person/firstName" exists (is non-null)
 * Constraint c1 = ExistsConstraint.exists("person/firstName");
 *
 * // Matches if any of the specified paths or their children exist
 * Constraint c2 = ExistsConstraint.exists(true, "address", "email");
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExistsConstraint extends PathConstraint {
    /**
     * Creates a constraint that checks for the presence of any of the given paths. Sub-paths will be included.
     *
     * @param paths the paths to check for existence
     * @return a constraint that matches if any of the given paths (or their sub-paths) exist
     */
    public static Constraint exists(String... paths) {
        return AnyConstraint.any(Arrays.stream(paths).<Constraint>map(ExistsConstraint::new).toList());
    }

    /**
     * Represents a non-null path within a document that is checked for existence.
     */
    @NonNull String exists;

    @JsonIgnore
    @Getter(lazy = true)
    List<String> paths = exists.endsWith("**") ? List.of(exists) : List.of(exists, exists + "/**");

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getType() != Document.EntryType.NULL;
    }

    @Override
    public Constraint withPaths(List<String> paths) {
        return paths.isEmpty() ? NoOpConstraint.instance : exists(paths.getFirst());
    }
}
