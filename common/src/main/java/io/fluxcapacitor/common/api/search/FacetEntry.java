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

package io.fluxcapacitor.common.api.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Comparator;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class FacetEntry implements Comparable<FacetEntry> {
    private static final Comparator<FacetEntry> comparator
            = Comparator.comparing(FacetEntry::getName).thenComparing(FacetEntry::getValue);

    @NonNull String name, value;

    @Override
    public int compareTo(@NonNull FacetEntry o) {
        return comparator.compare(this, o);
    }
}
