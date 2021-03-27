/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.search.DefaultDocumentSerializer;
import io.fluxcapacitor.common.search.Document;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SerializedDocument {
    String id;
    long timestamp;
    String collection;
    Data<byte[]> document;
    String summary;

    public SerializedDocument(Document document) {
        id = document.getId();
        timestamp = document.getTimestamp().toEpochMilli();
        collection = document.getCollection();
        this.document = DefaultDocumentSerializer.INSTANCE.serialize(document);
        summary = document.summarize();
    }

    public Document deserializeDocument() {
        return DefaultDocumentSerializer.INSTANCE.deserialize(document);
    }
}
