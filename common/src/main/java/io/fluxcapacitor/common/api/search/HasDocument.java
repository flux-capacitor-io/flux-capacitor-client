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
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A request to check whether a document exists in a given collection.
 * <p>
 * This is a lightweight operation used to determine the presence of a document based on its identifier
 * and the collection it belongs to without having to fetch the document.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * boolean exists = searchClient.documentExists(new HasDocument("invoice-1234", "invoices"));
 * }</pre>
 *
 * @see GetDocument
 * @see IndexDocumentIfNotExists
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class HasDocument extends Request {
    /**
     * The unique identifier of the document within the collection.
     */
    String id;

    /**
     * The name of the collection to check for document existence.
     */
    String collection;
}
