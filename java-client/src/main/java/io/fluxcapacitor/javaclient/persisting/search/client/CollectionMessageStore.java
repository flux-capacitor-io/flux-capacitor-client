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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.tracking.MessageStore;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@AllArgsConstructor
public class CollectionMessageStore implements MessageStore {

    private final InMemorySearchStore searchClient;
    private final String collection;

    @Override
    public List<SerializedMessage> getBatch(Long minIndex, int maxSize, boolean inclusive) {
        minIndex = minIndex == null ? 0L : minIndex;
        long lastIndex = inclusive ? minIndex -1L : minIndex;
        return searchClient.openStream(collection, lastIndex, maxSize).toList();
    }

    @Override
    public Registration registerMonitor(Consumer<List<SerializedMessage>> monitor) {
        return searchClient.registerMonitor(collection, monitor);
    }

    @Override
    public CompletableFuture<Void> append(List<SerializedMessage> messages) {
        throw new UnsupportedOperationException("Appending of documents is not supported");
    }

    @Override
    public void setRetentionTime(Duration retentionPeriod) {
        //no op
    }
}
