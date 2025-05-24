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
import io.fluxcapacitor.common.api.RequestResult;
import lombok.Value;

/**
 * Response to a {@link GetValue} request, returning the value associated with the given key.
 * <p>
 * The result includes the serialized form of the value (if found) and a timestamp marking when the value was retrieved.
 * </p>
 *
 * @see GetValue
 * @see Data
 */
@Value
public class GetValueResult implements RequestResult {

    /**
     * The ID correlating this response to its {@link GetValue} request.
     */
    long requestId;

    /**
     * The serialized value associated with the requested key, or {@code null} if not found.
     */
    Data<byte[]> value;

    /**
     * The timestamp at which the result was generated.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Lightweight representation of this response for metrics logging.
     */
    @Override
    public Metric toMetric() {
        return new Metric(timestamp);
    }

    @Value
    public static class Metric {
        /**
         * Time the result was returned.
         */
        long timestamp;
    }
}
