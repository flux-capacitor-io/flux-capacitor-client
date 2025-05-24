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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.fasterxml.jackson.annotation.JsonSetter.Value.empty;

/**
 * A custom Jackson module that configures deserialization behavior to treat null collections as empty collections. This
 * module sets up serialization and deserialization rules for common collection types such as {@code List}, {@code Set},
 * {@code Map}, and their variants, ensuring null values are handled consistently.
 * <p>
 * This module overrides Jackson's default behavior by: 1. Configuring the deserializer to replace null values with
 * empty collections when setting property values. 2. Ensuring that null values are always included during
 * serialization.
 * <p>
 * Supported collection types include: - {@code Collection} - {@code SequencedCollection} - {@code List} - {@code Set},
 * {@code SortedSet}, {@code SequencedSet} - {@code Map}, {@code SortedMap}, {@code SequencedMap}
 *
 * <p>
 * This module is enabled by default in {@link JsonUtils} and hence in {@code JacksonSerializer}.
 */
public class NullCollectionsAsEmptyModule extends SimpleModule {
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        JsonSetter.Value nullAsEmpty = empty().withValueNulls(Nulls.AS_EMPTY);
        JsonInclude.Value includeNull = JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.ALWAYS);
        context.configOverride(Collection.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(SequencedCollection.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(List.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(Set.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(SortedSet.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(SequencedSet.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(Map.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(SortedMap.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
        context.configOverride(SequencedMap.class).setSetterInfo(nullAsEmpty).setInclude(includeNull);
    }
}
