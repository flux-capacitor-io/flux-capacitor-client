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
import io.fluxcapacitor.common.search.Document;
import lombok.NonNull;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.search.Document.EntryType.NUMERIC;

@Value
public class BetweenConstraint extends PathConstraint {
    public static BetweenConstraint between(Object min, Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(min == null ? null : min.toString(),
                                     maxExclusive == null ? null : maxExclusive.toString(), path);
    }

    public static BetweenConstraint atLeast(@NonNull Object min, @NonNull String path) {
        return new BetweenConstraint(min.toString(), null, path);
    }

    public static BetweenConstraint below(@NonNull Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(null, maxExclusive.toString(), path);
    }

    String min;
    String max;
    String path;
    @JsonIgnore
    Predicate<String> valuePredicate;

    @ConstructorProperties({"min", "max", "path"})
    public BetweenConstraint(String min, String max, @NonNull String path) {
        this.min = min;
        this.max = max;
        this.path = path;
        this.valuePredicate = min == null ? max == null ? s -> true : s -> s.compareTo(max) < 0 : max == null
                ? s -> s.compareTo(min) >= 0 : s -> s.compareTo(min) >= 0 && s.compareTo(max) < 0;
    }

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getType() == NUMERIC && getValuePredicate().test(entry.getValue());
    }
}
