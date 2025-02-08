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

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.tracking.handling.Stateful;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

@Slf4j
public class DefaultHandlerRepository implements HandlerRepository {

    public static Function<Class<?>, HandlerRepository> repositorySupplier(Supplier<DocumentStore> documentStore) {
        return memoize(type -> new DefaultHandlerRepository(
                documentStore.get(), ClientUtils.getSearchParameters(type).getCollection(),
                type, ReflectionUtils.getTypeAnnotation(type, Stateful.class)));
    }

    private final DocumentStore documentStore;
    private final String collection;
    private final Class<?> type;
    private final Function<Object, Instant> timestampFunction;
    private final Function<Object, Instant> endFunction;

    public DefaultHandlerRepository(DocumentStore documentStore, String collection, Class<?> type, Stateful annotation) {
        this.documentStore = documentStore;
        this.collection = collection;
        this.type = type;
        AtomicBoolean warnedAboutMissingTimePath = new AtomicBoolean();
        this.timestampFunction = Optional.of(annotation).map(Stateful::timestampPath)
                .filter(path -> !path.isBlank())
                .<Function<Object, Instant>>map(path -> handler -> ReflectionUtils.readProperty(path, handler)
                        .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                            if (handler != null) {
                                if (ReflectionUtils.hasProperty(path, handler)) {
                                    return null;
                                }
                                if (warnedAboutMissingTimePath.compareAndSet(false, true)) {
                                    log.warn("Type {} does not declare a timestamp property at '{}'",
                                             handler.getClass().getSimpleName(), path);
                                }
                            }
                            return FluxCapacitor.currentTime();
                        })).orElseGet(() -> handler -> FluxCapacitor.currentTime());
        AtomicBoolean warnedAboutMissingEndPath = new AtomicBoolean();
        this.endFunction = Optional.of(annotation).map(Stateful::endPath)
                .filter(path -> !path.isBlank())
                .<Function<Object, Instant>>map(path -> handler -> ReflectionUtils.readProperty(path, handler)
                        .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                            if (handler != null) {
                                if (ReflectionUtils.hasProperty(path, handler)) {
                                    return null;
                                }
                                if (warnedAboutMissingEndPath.compareAndSet(false, true)) {
                                    log.warn("Type {} does not declare an end timestamp property at '{}'",
                                             handler.getClass().getSimpleName(), path);
                                }
                            }
                            return FluxCapacitor.currentTime();
                        }))
                .orElse(timestampFunction);
    }

    @Override
    public Collection<? extends Entry<Object>> findByAssociation(Map<Object, String> associations) {
        if (associations.isEmpty()) {
            return Collections.emptyList();
        }
        var constraints = associations.entrySet().stream().map(e -> MatchConstraint.match(
                e.getKey(), e.getValue())).toArray(Constraint[]::new);
        return documentStore.search(collection).any(constraints).streamHits()
                .filter(h -> type.isAssignableFrom(h.getValue().getClass())).toList();
    }

    @Override
    public Collection<? extends Entry<?>> getAll() {
        return documentStore.search(collection).streamHits()
                .filter(h -> type.isAssignableFrom(h.getValue().getClass())).toList();
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
        return documentStore.index(value, id, collection, timestampFunction.apply(value), endFunction.apply(value));
    }

    @SneakyThrows
    public CompletableFuture<?> delete(Object id) {
        return documentStore.deleteDocument(id, collection);
    }
}
