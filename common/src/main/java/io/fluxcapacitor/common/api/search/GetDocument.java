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

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Request to fetch a single document from the search store by ID and collection.
 * <p>
 * This is typically used to retrieve the latest indexed version of a document for inspection or
 * further processing. If no document is found, an empty result is returned.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class GetDocument extends Request {
    /**
     * The unique ID of the document to fetch.
     */
    String id;

    /**
     * The collection in which the document is stored.
     */
    String collection;
}
