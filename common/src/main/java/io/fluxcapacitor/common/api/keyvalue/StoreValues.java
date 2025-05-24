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

/**
 * <strong>Legacy API:</strong> This command stores key-value pairs in the legacy key-value store mechanism.
 * <p>
 * While still supported, this approach is considered obsolete and retained primarily for backward compatibility.
 * For most use cases, prefer using {@code DocumentStore} for flexible and queryable persistence.
 * </p>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code values} – A list of key-value pairs to store</li>
 *   <li>{@code guarantee} – Defines delivery/storage guarantees (e.g., {@code Guarantee.STORED})</li>
 * </ul>
 *
 * <h2>Routing</h2>
 * The first key in the value list is used as the routing key for load distribution.
 *
 * <h2>Metrics</h2>
 * The command emits a lightweight {@link Metric} representation with the keys and count.
 *
 * @see KeyValuePair
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class StoreValues extends Command {
    List<KeyValuePair> values;
    Guarantee guarantee;

    @Override
    public Object toMetric() {
        return new Metric(values.stream().map(KeyValuePair::getKey).collect(toList()), values.size());
    }

    @Override
    public String toString() {
        return "StoreValues of size " + values.size();
    }

    @Override
    public String routingKey() {
        return values.getFirst().getKey();
    }

    /**
     * Lightweight metric representation of this command.
     */
    @Value
    public static class Metric {
        List<String> keys;
        int size;
    }
}
