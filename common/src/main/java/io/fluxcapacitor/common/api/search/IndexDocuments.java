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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command used to index a collection of {@link SerializedDocument} instances in the search store.
 * <p>
 * This command is typically sent to the Flux platform, requesting that the specified documents be stored and made
 * searchable.
 * <p>
 * Documents can belong to different collections and contain arbitrary facets, indexes, and metadata, allowing them to
 * be used for filtering, searching, and analytics.
 *
 * <p><strong>Conditional indexing:</strong> If {@code ifNotExists} is {@code true}, the platform will only index a
 * document if no document with the same ID and collection already exists.
 *
 * <p><strong>Delivery guarantees:</strong> The {@link Guarantee} determines the durability of the indexing operation
 * (e.g. {@code STORED} waits for acknowledgment).
 *
 * @see io.fluxcapacitor.common.Guarantee
 * @see SerializedDocument
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class IndexDocuments extends Command {
    List<SerializedDocument> documents;
    boolean ifNotExists;
    Guarantee guarantee;

    @JsonIgnore
    public int getSize() {
        return documents.size();
    }

    @JsonIgnore
    long getBytes() {
        return documents.stream().mapToLong(SerializedDocument::bytes).sum();
    }

    @JsonIgnore
    Set<String> getCollections() {
        return documents.stream().map(SerializedDocument::getCollection).collect(Collectors.toSet());
    }

    @JsonIgnore
    Set<String> getIds() {
        return documents.stream().map(SerializedDocument::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public String toString() {
        return "IndexDocuments of length " + documents.size();
    }

    @Override
    public Metric toMetric() {
        return new Metric(getSize(), ifNotExists, guarantee, getCollections(), getIds(), getBytes());
    }

    @Override
    public String routingKey() {
        return documents.stream().findFirst().map(SerializedDocument::getId).orElse(null);
    }

    @Value
    public static class Metric {
        int size;
        boolean ifNotExists;
        Guarantee guarantee;
        Set<String> collections;
        Set<String> ids;
        long bytes;
    }
}
