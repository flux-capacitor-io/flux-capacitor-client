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

package io.fluxcapacitor.common.search;

import io.fluxcapacitor.common.api.Data;

import java.time.Instant;

public interface Inverter<T> {
    default Document toDocument(Data<byte[]> data, String id, String collection) {
        return toDocument(data, id, collection, null, null);
    }

    default Document toDocument(Data<byte[]> data, String id, String collection, Instant timestamp) {
        return toDocument(data, id, collection, timestamp, timestamp);
    }

    Document toDocument(Data<byte[]> data, String id, String collection, Instant timestamp, Instant end);

    T fromDocument(Document document);
}
