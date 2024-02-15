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

import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@AllArgsConstructor
public class DefaultViewRepository implements ViewRepository {
    public static Function<Class<?>, ViewRepository> repositorySupplier(DocumentStore documentStore) {
        return type -> new DefaultViewRepository(documentStore, ClientUtils.determineCollection(type));
    }

    private final DocumentStore documentStore;
    private final String collection;

    @Override
    public Collection<? extends Entry<Object>> findByAssociation(Collection<String> associations) {
        if (associations.isEmpty()) {
            return Collections.emptyList();
        }
        return documentStore.search(collection).match(associations).streamHits().toList();
    }

    @Override
    public Entry<?> get(Object id) {
        return documentStore.fetchDocument(id, collection).map(v -> new Entry<>() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Object getValue() {
                return v;
            }
        }).orElse(null);
    }

    @SneakyThrows
    public CompletableFuture<?> set(Object value, Object id) {
        return documentStore.index(value, id, collection);
    }

    @SneakyThrows
    public CompletableFuture<?> delete(Object id) {
        return documentStore.deleteDocument(id, collection);
    }
}
