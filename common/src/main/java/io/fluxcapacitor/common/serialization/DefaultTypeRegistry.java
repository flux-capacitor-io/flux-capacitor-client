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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getSimpleName;

@Slf4j
public class DefaultTypeRegistry implements TypeRegistry {

    @SneakyThrows
    static List<String> loadAllRegisteredTypes() {
        List<String> allTypes = new ArrayList<>();
        var resources = ClassLoader.getSystemClassLoader().getResources(TypeRegistryProcessor.TYPES_FILE);
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    allTypes.add(line);
                }
            }
        }
        return allTypes;
    }

    private final SortedSet<String> fullTypeNames;
    private final Map<String, String> types;

    public DefaultTypeRegistry() {
        this(loadAllRegisteredTypes());
    }

    protected DefaultTypeRegistry(List<String> candidates) {
        this.fullTypeNames = new TreeSet<>(candidates);
        Map<String, String> types = new TreeMap<>();
        for (String fqn : candidates) {
            types.putIfAbsent(getSimpleName(fqn), fqn);
        }
        this.types = types;
    }

    @Override
    public Optional<String> getTypeName(String alias) {
        return Optional.ofNullable(types.get(alias)).or(() -> {
            var suffix = (alias.startsWith(".") ? alias : "." + alias);
            return fullTypeNames.stream().filter(t -> t.endsWith(suffix)).findFirst();
        });
    }

}
