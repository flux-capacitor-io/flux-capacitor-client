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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.tracking.handling.View;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Slf4j
public class DefaultViewRepository implements ViewRepository {
    public static Function<Class<?>, ViewRepository> repositorySupplier(DocumentStore documentStore) {
        return type -> new DefaultViewRepository(documentStore, ClientUtils.determineCollection(type),
                                                 ReflectionUtils.getTypeAnnotation(type, View.class));
    }

    private final DocumentStore documentStore;
    private final String collection;
    private final Function<Object, Instant> timestampFunction;

    public DefaultViewRepository(DocumentStore documentStore, String collection, View annotation) {
        this.documentStore = documentStore;
        this.collection = collection;
        AtomicBoolean warnedAboutMissingTimePath = new AtomicBoolean();
        this.timestampFunction = Optional.of(annotation).map(View::timestampPath)
                .filter(path -> !path.isBlank())
                .<Function<Object, Instant>>map(path -> view -> ReflectionUtils.readProperty(path, view)
                        .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                            if (warnedAboutMissingTimePath.compareAndSet(false, true)) {
                                log.warn("Type {} does not declare a timestamp property at '{}'",
                                         view.getClass().getSimpleName(), path);
                            }
                            return FluxCapacitor.currentTime();
                        })).orElseGet(() -> view -> FluxCapacitor.currentTime());
    }

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
        return documentStore.index(value, id, collection, timestampFunction.apply(value));
    }

    @SneakyThrows
    public CompletableFuture<?> delete(Object id) {
        return documentStore.deleteDocument(id, collection);
    }
}
