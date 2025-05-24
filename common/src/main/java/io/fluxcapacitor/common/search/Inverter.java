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

package io.fluxcapacitor.common.search;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.serialization.Converter;
import io.fluxcapacitor.common.serialization.Revision;

import java.time.Instant;

/**
 * Interface responsible for converting a domain object into a {@link SerializedDocument} for indexing, and vice
 * versa—deserializing a {@code byte[]} representation into the original object type.
 * <p>
 * An {@code Inverter} is a specialized {@link Converter} used in the search module. It bridges the gap between
 * serializable objects and searchable document formats. Implementations handle both document creation and
 * deserialization from raw data.
 *
 * @param <T> the type of object that can be inverted and deserialized
 * @see SerializedDocument
 */
public interface Inverter<T> extends Converter<byte[], T> {

    /**
     * Converts the given object into a {@link SerializedDocument}, which is suitable for indexing or storage in a
     * document store.
     *
     * @param object     the original object to convert
     * @param type       the simple type name of the object
     * @param revision   the revision number of the object (e.g., as provided by {@link Revision})
     * @param id         the unique ID for this document within the given collection
     * @param collection the name of the collection the document belongs to
     * @param timestamp  the optional starting timestamp for the document's validity
     * @param end        the optional end timestamp of the document's validity
     * @param metadata   optional metadata to associate with the document
     * @return a fully constructed {@link SerializedDocument}
     */
    SerializedDocument toDocument(Object object, String type, int revision, String id, String collection,
                                  Instant timestamp, Instant end, Metadata metadata);
}
