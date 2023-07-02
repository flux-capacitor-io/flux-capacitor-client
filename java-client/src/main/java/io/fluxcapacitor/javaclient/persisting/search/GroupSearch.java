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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.api.search.DocumentStats.FieldStats;
import io.fluxcapacitor.common.api.search.Group;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

public interface GroupSearch {

    Map<Group, Map<String, FieldStats>> aggregate(String... fields);

    default Map<Group, Long> count() {
        return aggregate().entrySet().stream().collect(toMap(Map.Entry::getKey, e ->
                e.getValue().values().stream().findFirst().map(FieldStats::getCount).orElse(0L)));
    }

}
