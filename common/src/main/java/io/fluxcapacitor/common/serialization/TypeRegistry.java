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

package io.fluxcapacitor.common.serialization;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
public abstract class TypeRegistry {

    private final SortedSet<String> fullTypeNames;
    private final Map<String, String> types;

    protected TypeRegistry(List<String> candidates) {
        this.fullTypeNames = new TreeSet<>(candidates);
        Map<String, String> types = new TreeMap<>();
        for (String fqn : candidates) {
            types.putIfAbsent(getSimpleName(fqn), fqn);
        }
        this.types = types;
    }

    public Optional<String> getTypeName(String alias) {
        return Optional.ofNullable(types.get(alias)).or(() -> {
            var suffix = (alias.startsWith(".") ? alias : "." + alias);
            return fullTypeNames.stream().filter(t -> t.endsWith(suffix)).findFirst();
        });
    }

    static String getSimpleName(String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Fully qualified name cannot be null or empty");
        }
        int lastSeparatorIndex = Math.max(fullyQualifiedName.lastIndexOf('.'), fullyQualifiedName.lastIndexOf('$'));
        return (lastSeparatorIndex == -1) ? fullyQualifiedName : fullyQualifiedName.substring(lastSeparatorIndex + 1);
    }


}
