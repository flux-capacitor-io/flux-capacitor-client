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
import lombok.Value;

/**
 * Command to delete a value from the legacy key-value store by key.
 * <p>
 * This removes the value associated with the specified {@code key} from the key-value store.
 * This command is part of the legacy storage system and is primarily relevant in older Flux applications
 * that still use {@code StoreValues} or {@code StoreValueIfAbsent}.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If the key does not exist, the operation is a no-op.</li>
 *   <li>The deletion is governed by the specified {@link Guarantee}.</li>
 * </ul>
 *
 * <h2>Usage Notes</h2>
 * <ul>
 *   <li>For most modern use cases, consider using the document store or {@code SearchClient} instead.</li>
 *   <li>This command is typically used for cleanup, invalidation, or state transitions in legacy flows.</li>
 * </ul>
 *
 * @see StoreValues
 * @see StoreValueIfAbsent
 * @see Guarantee
 */
@Value
public class DeleteValue extends Command {

    /**
     * The key of the entry to delete.
     */
    String key;

    /**
     * Persistence guarantee to apply to the deletion.
     */
    Guarantee guarantee;

    /**
     * Routing key used by the Flux platform to shard or partition this command.
     *
     * @return the key being deleted
     */
    @Override
    public String routingKey() {
        return key;
    }
}
