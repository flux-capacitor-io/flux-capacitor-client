/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.fasterxml.jackson.annotation.JsonSetter.Value.empty;

public class NullCollectionsAsEmptyModule extends SimpleModule {
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        JsonSetter.Value nullAsEmpty = empty().withValueNulls(Nulls.AS_EMPTY);
        context.configOverride(Collection.class).setSetterInfo(nullAsEmpty);
        context.configOverride(List.class).setSetterInfo(nullAsEmpty);
        context.configOverride(Set.class).setSetterInfo(nullAsEmpty);
        context.configOverride(SortedSet.class).setSetterInfo(nullAsEmpty);
        context.configOverride(Map.class).setSetterInfo(nullAsEmpty);
        context.configOverride(SortedMap.class).setSetterInfo(nullAsEmpty);
    }
}
