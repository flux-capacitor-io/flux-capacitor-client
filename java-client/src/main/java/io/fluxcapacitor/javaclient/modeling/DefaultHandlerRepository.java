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
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.tracking.handling.Stateful;
import lombok.AccessLevel;
import lombok.Getter;
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

/**
 * Default implementation of {@link HandlerRepository}, backed by a {@link DocumentStore}.
 * <p>
 * This repository provides direct indexing and retrieval of {@code @Stateful} handlers, using values from fields and
 * methods annotated with {@link io.fluxcapacitor.javaclient.tracking.handling.Association}.
 * <p>
 * Timestamp information can optionally be derived from paths specified in the
 * {@link Stateful} annotation on the handler class.
 */
@Slf4j
@Getter(AccessLevel.PROTECTED)
public class DefaultHandlerRepository implements HandlerRepository {

    /**
     * Returns a factory function that creates a {@link HandlerRepository} for a given handler type.
     * <p>
     * If the handler type is annotated with {@code @Stateful(commitInBatch = true)}, the returned repository will
     * buffer updates and commit them as a batch using {@link BatchingHandlerRepository}.
     *
     * @param documentStore      the underlying document store supplier
     * @param documentSerializer serializer used for creating {@link SerializedDocument} objects
     * @return a factory function that returns a suitable {@link HandlerRepository} for each handler class
     */
    public static Function<Class<?>, HandlerRepository> handlerRepositorySupplier(Supplier<DocumentStore> documentStore,
                                                                                  DocumentSerializer documentSerializer) {
        return memoize(type -> {
            Stateful stateful = ReflectionUtils.getTypeAnnotation(type, Stateful.class);
            var defaultRepo = new DefaultHandlerRepository(
                    documentStore.get(), ClientUtils.getSearchParameters(type).getCollection(), type, stateful);
            return Optional.ofNullable(stateful).filter(Stateful::commitInBatch)
                    .<HandlerRepository>map(s -> new BatchingHandlerRepository(
                            defaultRepo, documentSerializer)).orElse(defaultRepo);
        });
    }

    private final DocumentStore documentStore;
    private final String collection;
    private final Class<?> type;
    private final Function<Object, Instant> timestampFunction;
    private final Function<Object, Instant> endFunction;

    public DefaultHandlerRepository(DocumentStore documentStore, String collection, Class<?> type,
                                    Stateful annotation) {
        this.documentStore = documentStore;
        this.collection = collection;
        this.type = type;

        // Resolve timestamp extraction logic
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

        // Resolve end timestamp extraction logic
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
        var constraint = asConstraint(associations);
        return documentStore.search(collection).constraint(constraint).streamHits()
                .filter(h -> type.isAssignableFrom(h.getValue().getClass())).toList();
    }

    protected Constraint asConstraint(Map<Object, String> associations) {
        return AnyConstraint.any(associations.entrySet().stream().map(e -> MatchConstraint.match(
                e.getKey(), e.getValue())).toArray(Constraint[]::new));
    }

    @Override
    public Collection<? extends Entry<?>> getAll() {
        return documentStore.search(collection).streamHits()
                .filter(h -> type.isAssignableFrom(h.getValue().getClass())).toList();
    }

    @Override
    @SneakyThrows
    public CompletableFuture<?> put(Object id, Object value) {
        return documentStore.index(value, id, collection, timestampFunction.apply(value), endFunction.apply(value));
    }

    @Override
    @SneakyThrows
    public CompletableFuture<?> delete(Object id) {
        return documentStore.deleteDocument(id, collection);
    }
}
