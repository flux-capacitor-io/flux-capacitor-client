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
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExistsConstraint extends PathConstraint {
    public static Constraint exists(String... paths) {
        return exists(true, paths);
    }

    public static Constraint exists(boolean includeSubPaths, String... paths) {
        if (paths.length == 0) {
            return NoOpConstraint.instance;
        }
        if (includeSubPaths) {
            List<String> allPaths = new ArrayList<>(paths.length * 2);
            for (String path : paths) {
                allPaths.add(path);
                if (!path.endsWith("**")) {
                    allPaths.add(path + "/**");
                }
            }
            return new ExistsConstraint(allPaths);
        }
        return new ExistsConstraint(Arrays.asList(paths));
    }

    @With
    @NonNull List<String> exists;

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getType() != Document.EntryType.NULL;
    }

    @Override
    @JsonIgnore
    public List<String> getPaths() {
        return exists;
    }

    @Override
    public Constraint withPaths(List<String> paths) {
        return paths.isEmpty() ? NoOpConstraint.instance : withExists(paths);
    }
}
