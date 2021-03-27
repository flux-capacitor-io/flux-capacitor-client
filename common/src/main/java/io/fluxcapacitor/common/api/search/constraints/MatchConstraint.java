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
import lombok.NonNull;
import lombok.Value;

import java.util.Arrays;
import java.util.stream.Collectors;

@Value
public class MatchConstraint extends PathConstraint {
    public static Constraint match(@NonNull Object value, String... paths) {
        switch (paths.length) {
            case 0: return new MatchConstraint(value.toString(), null);
            case 1: return new MatchConstraint(value.toString(), paths[0]);
            default: return new AnyConstraint(Arrays.stream(paths).map(
                    p -> new MatchConstraint(value.toString(), p)).collect(Collectors.toList()));
        }
    }

    String value;
    String path;

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getValue().equals(value);
    }
}
