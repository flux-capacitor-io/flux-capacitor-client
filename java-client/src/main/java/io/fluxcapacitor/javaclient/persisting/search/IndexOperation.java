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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface IndexOperation {

    default CompletableFuture<Void> index() {
        return index(Guarantee.STORED);
    }

    @SneakyThrows
    default void indexAndForget() {
        index(Guarantee.NONE);
    }

    @SneakyThrows
    default void indexAndWait() {
        indexAndWait(Guarantee.STORED);
    }

    @SneakyThrows
    default void indexAndWait(Guarantee guarantee) {
        index(guarantee).get();
    }

    CompletableFuture<Void> index(Guarantee guarantee);

    IndexOperation id(@NonNull Object id);

    IndexOperation collection(@NonNull Object collection);

    IndexOperation start(Instant start);

    IndexOperation end(Instant end);

    default IndexOperation timestamp(Instant timestamp) {
        return start(timestamp).end(timestamp);
    }

    default IndexOperation period(Instant start, Instant end) {
        return start(start).end(end);
    }

    default IndexOperation addMetadata(@NonNull Metadata metadata) {
        return metadata(metadata().with(metadata));
    }

    default IndexOperation addMetadata(@NonNull Object key, Object value) {
        return metadata(metadata().with(key, value));
    }

    default IndexOperation addMetadata(@NonNull Map<String, ?> values) {
        return metadata(metadata().with(values));
    }

    IndexOperation metadata(Metadata metadata);

    IndexOperation ifNotExists(boolean toggle);

    Instant start();

    Instant end();

    Object id();

    Metadata metadata();

    boolean ifNotExists();

    IndexOperation copy();

}
