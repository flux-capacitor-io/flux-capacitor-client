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

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Request to retrieve a value from the legacy key-value store.
 * <p>
 * This request returns the serialized value associated with the specified {@code key}, if present. It is part of the
 * legacy key-value subsystem, which has largely been superseded by the document store.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If the key is not found, the returned {@link io.fluxcapacitor.common.api.Data} will be {@code null} or empty depending on usage context.</li>
 *   <li>The value is returned as raw serialized data in {@link io.fluxcapacitor.common.api.Data} format.</li>
 * </ul>
 *
 * <h2>Usage Notes</h2>
 * <ul>
 *   <li>This API is mostly retained for backwards compatibility. Prefer using the {@code SearchClient} for modern data queries and lookups.</li>
 *   <li>The result is returned in a {@link GetValueResult} response.</li>
 * </ul>
 *
 * @see GetValueResult
 * @see StoreValues
 * @see DeleteValue
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class GetValue extends Request {

    /**
     * The key to retrieve from the store.
     */
    String key;
}
