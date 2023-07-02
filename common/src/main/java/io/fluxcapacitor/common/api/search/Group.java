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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
public class Group {

    public static Group of(String... pathsAndValues) {
        if (pathsAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Number of values should be even");
        }
        LinkedHashMap<String, String> map = new LinkedHashMap<>(pathsAndValues.length/2);
        for (int i = 0; i < pathsAndValues.length; i += 2) {
            map.put(pathsAndValues[i], pathsAndValues[i+1]);
        }
        return new Group(map);
    }

    @JsonValue
    Map<String, String> values;

    @JsonCreator
    public Group(Map<String, String> values) {
        this.values = values;
    }

    public String get(String path) {
        return values.get(path);
    }
}
