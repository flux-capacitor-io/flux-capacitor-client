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
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.NonNull;
import lombok.Value;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
public class MatchConstraint extends PathConstraint {
    public static Constraint match(@NonNull Object value, String... paths) {
        switch (paths.length) {
            case 0: return matchForPath(value, null);
            case 1: return matchForPath(value, paths[0]);
            default: return new AnyConstraint(Arrays.stream(paths).map(p -> matchForPath(value, p)).collect(toList()));
        }
    }

    protected static Constraint matchForPath(@NonNull Object value, String path) {
        if (value instanceof Collection<?>) {
            List<Constraint> constraints =
                    ((Collection<?>) value).stream().map(v -> new MatchConstraint(v.toString(), path))
                            .collect(toList());
            switch (constraints.size()) {
                case 0: return NoOpConstraint.instance;
                case 1: return constraints.get(0);
                default: return new AnyConstraint(constraints);
            }
        } else {
            return new MatchConstraint(value.toString(), path);
        }
    }

    @NonNull String match;
    String path;

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getValue().equals(match);
    }
}
