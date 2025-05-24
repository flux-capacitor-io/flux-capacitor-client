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

package io.fluxcapacitor.common.api;

import lombok.Value;

/**
 * A generic response containing a string value.
 * <p>
 * This is used when a request yields a simple string-based result (e.g. for diagnostics or simple identifiers).
 * </p>
 *
 * @see RequestResult
 */
@Value
public class StringResult implements RequestResult {

    /**
     * ID correlating this result with its originating request.
     */
    long requestId;

    /**
     * Time at which the result was generated.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * The result string.
     */
    String result;
}