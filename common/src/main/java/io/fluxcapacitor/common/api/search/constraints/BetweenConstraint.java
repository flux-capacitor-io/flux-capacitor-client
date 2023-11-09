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

import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.SearchUtils.formatValue;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BetweenConstraint extends PathConstraint {

    public static BetweenConstraint between(Object min, Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(formatValue(min), formatValue(maxExclusive), List.of(path));
    }

    public static BetweenConstraint atLeast(@NonNull Object min, @NonNull String path) {
        return new BetweenConstraint(formatValue(min), null, List.of(path));
    }

    public static BetweenConstraint below(@NonNull Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(null, formatValue(maxExclusive), List.of(path));
    }

    String min;
    String max;
    @With
    List<String> paths;

    @Override
    protected boolean matches(Document.Entry entry) {
        return matcher().test(entry.getValue());
    }

    @Override
    protected boolean checkPathBeforeEntry() {
        return true;
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Predicate<String> matcher =
            min == null ? max == null ? s -> true : s -> s.compareTo(max) < 0 : max == null
                    ? s -> s.compareTo(min) >= 0 : s -> s.compareTo(min) >= 0 && s.compareTo(max) < 0;
}
