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

import java.util.Collection;

/**
 * Request to fetch multiple documents from the search store by collection and their IDs.
 * <p>
 * This is typically used to retrieve the latest indexed versions of the requested documents for inspection or
 * further processing. If no documents are found, an empty collection is returned.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class GetDocuments extends Request {
    /**
     * The unique IDs of the documents to fetch.
     */
    Collection<String> ids;

    /**
     * The collection in which the documents are stored.
     */
    String collection;
}
