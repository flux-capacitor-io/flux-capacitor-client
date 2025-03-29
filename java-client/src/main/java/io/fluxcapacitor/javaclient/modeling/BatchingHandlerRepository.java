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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.computeForBatchIfAbsent;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.getCurrent;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenBatchCompletes;

@AllArgsConstructor
public class BatchingHandlerRepository implements HandlerRepository {

    private final DefaultHandlerRepository delegate;
    private final DocumentSerializer documentSerializer;

    @Override
    public Collection<? extends Entry<?>> findByAssociation(Map<Object, String> associations) {
        if (associations.isEmpty()) {
            return Collections.emptyList();
        }
        var query = SearchQuery.builder().constraint(delegate.asConstraint(associations))
                .collection(delegate.getCollection()).build();
        return Stream.concat(updates().values().stream().filter(u -> query.matches(u.getDocument())),
                             removeOutdatedValues(delegate.findByAssociation(associations))).toList();
    }

    @Override
    public Collection<? extends Entry<?>> getAll() {
        return Stream.concat(updates().values().stream(),
                             removeOutdatedValues(delegate.getAll())).toList();
    }

    protected Stream<? extends Entry<?>> removeOutdatedValues(Collection<? extends Entry<?>> delegateResult) {
        var updates = updates();
        return delegateResult.stream().filter(e -> !updates.containsKey(e.getId()));
    }

    @Override
    public CompletableFuture<?> put(Object id, Object value) {
        if (getCurrent() == null) {
            return value == null ? delegate.delete(id) : delegate.put(id, value);
        }
        updates().put(id.toString(), new Update(id.toString(), value));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> delete(Object id) {
        return put(id, null);
    }

    protected Map<Object, Update> updates() {
        return computeForBatchIfAbsent(this, __ -> {
            Map<Object, Update> map = new LinkedHashMap<>();
            whenBatchCompletes(e -> flushUpdates(map));
            return map;
        });
    }

    @SneakyThrows
    protected void flushUpdates(Map<Object, Update> map) {
        List<BulkUpdate> updates = map.values().stream().map(update -> update.getValue() == null ?
                DeleteDocument.builder().id(update.getId()).collection(delegate.getCollection())
                        .build() : IndexDocument.fromDocument(update.getDocument())).toList();
        delegate.getDocumentStore().bulkUpdate(updates).get();
    }

    @Value
    protected class Update implements Entry<Object> {
        String id;
        Object value;

        @Getter(lazy = true)
        SerializedDocument document = value == null ? null : documentSerializer.toDocument(
                value, id, delegate.getCollection(), delegate.getTimestampFunction().apply(value),
                delegate.getEndFunction().apply(value));
    }
}
