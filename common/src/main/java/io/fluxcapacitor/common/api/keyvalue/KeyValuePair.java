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

import io.fluxcapacitor.common.api.Data;
import lombok.Value;

/**
 * Represents a single key-value entry in the legacy key-value store.
 * <p>
 * This structure is primarily used in {@link StoreValues} commands to persist arbitrary binary data.
 * </p>
 */
@Value
public class KeyValuePair {
    /**
     * The unique key under which the value will be stored.
     */
    String key;

    /**
     * The binary data associated with the key, wrapped in {@link Data}.
     */
    Data<byte[]> value;
}
