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

/**
 * Stores a key-value pair <strong>only if</strong> the specified key does not already exist in the key-value store.
 * <p>
 * This command ensures atomic "put-if-absent" semantics, making it useful for distributed locks, idempotency markers,
 * or one-time setup flags in legacy Flux applications.
 * </p>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>Always uses {@link Guarantee#STORED} for persistence safety.</li>
 *   <li>This command is part of the legacy key-value subsystem. For most modern use cases, consider migrating
 *       to the {@code DocumentStore} or {@code Searchable} storage model.</li>
 * </ul>
 *
 * @see KeyValuePair
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class StoreValueIfAbsent extends Command {
    KeyValuePair value;

    @Override
    public Guarantee getGuarantee() {
        return Guarantee.STORED;
    }

    @Override
    public String toString() {
        return "StoreValueIfAbsent for key: " + value.getKey();
    }

    @Override
    public Metric toMetric() {
        return new Metric(value.getKey());
    }

    @Override
    public String routingKey() {
        return value.getKey();
    }

    /**
     * Lightweight metric representation for logging or monitoring.
     */
    @Value
    public static class Metric {
        String key;
    }
}
