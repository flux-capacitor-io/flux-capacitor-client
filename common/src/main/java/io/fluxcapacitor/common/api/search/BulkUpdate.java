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

import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;

import java.time.Instant;

public interface BulkUpdate {
    String getId();
    String getCollection();
    Type getType();

    static IndexDocument index(Object object, String id, String collection, Instant timestamp, Instant end) {
        return new IndexDocument(object, id, collection, timestamp, end);
    }

    static IndexDocumentIfNotExists indexIfNotExists(Object object, String id, String collection, Instant timestamp, Instant end) {
        return new IndexDocumentIfNotExists(object, id, collection, timestamp, end);
    }
    static DeleteDocument delete(String id, String collection) {
        return new DeleteDocument(id, collection);
    }


    enum Type {
        index, indexIfNotExists, delete;
    }
}
