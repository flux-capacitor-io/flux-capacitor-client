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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExistsConstraint extends PathConstraint {
    public static Constraint exists(String path) {
        return path == null ? NoOpConstraint.instance : new ExistsConstraint(path);
    }

    @NonNull String exists;

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getType() != Document.EntryType.NULL;
    }

    @Override
    @JsonIgnore
    public List<String> getPaths() {
        return List.of(exists);
    }

    @Override
    public Constraint withPaths(List<String> paths) {
        return paths.isEmpty() ? NoOpConstraint.instance : exists(paths.get(0));
    }

    @Override
    public boolean hasTextConstraint() {
        return false;
    }
}
