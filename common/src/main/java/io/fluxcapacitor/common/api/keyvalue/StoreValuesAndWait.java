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

package io.fluxcapacitor.common.api.keyvalue;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = true)
public class StoreValuesAndWait extends Command {
    List<KeyValuePair> values;

    @Override
    public Guarantee getGuarantee() {
        return Guarantee.STORED;
    }

    @Override
    public String routingKey() {
        return values.get(0).getKey();
    }

    @Override
    public String toString() {
        return "StoreValuesAndWait of size " + values.size();
    }

    @Override
    public Object toMetric() {
        return new Metric(values.stream().map(KeyValuePair::getKey).collect(toList()), values.size());
    }

    @Value
    public static class Metric {
        List<String> keys;
        int size;
    }
}
