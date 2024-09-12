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
