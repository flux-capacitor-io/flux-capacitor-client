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

import io.fluxcapacitor.javaclient.common.Entry;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.computeForBatchIfAbsent;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.getCurrent;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenBatchCompletes;

@AllArgsConstructor
@Slf4j
public class BatchingHandlerRepository implements HandlerRepository {

    private final HandlerRepository delegate;

    @Override
    public Collection<? extends Entry<?>> findByAssociation(Map<Object, String> associations) {
        return updateWithCurrentEntries(delegate.findByAssociation(associations));
    }

    @Override
    public Collection<? extends Entry<?>> getAll() {
        return updateWithCurrentEntries(delegate.getAll());
    }

    protected Collection<? extends Entry<?>> updateWithCurrentEntries(Collection<? extends Entry<?>> delegateResult) {
        var currentHandlers = handlersForBatch();
        return delegateResult.stream().map(e -> Optional.ofNullable(currentHandlers.get(e.getId()))
                .<Entry<?>>map(v -> new SimpleEntry(e.getId(), v)).orElse(e)).toList();
    }

    @Override
    public Entry<?> get(Object id) {
        return new SimpleEntry(id, handlersForBatch().computeIfAbsent(id.toString(), k -> delegate.get(k).getValue()));
    }

    @Override
    public CompletableFuture<?> put(Object id, Object value) {
        if (getCurrent() == null) {
            return delegate.put(id, value);
        }
        handlersForBatch().put(id.toString(), value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> delete(Object id) {
        return getCurrent() == null ? delegate.delete(id) : put(id, null);
    }

    @Override
    public CompletableFuture<?> put(Map<Object, Object> handlersById) {
        return delegate.put(handlersById);
    }

    protected Map<Object, Object> handlersForBatch() {
        return computeForBatchIfAbsent(this, __ -> {
            Map<Object, Object> map = new LinkedHashMap<>();
            whenBatchCompletes(e -> putAndWait(map));
            return map;
        });
    }

    @SneakyThrows
    protected void putAndWait(Map<Object, Object> map) {
        put(map).get();
    }

    @Value
    protected static class SimpleEntry implements Entry<Object> {
        Object id, value;
    }

    protected static class LocalHandlerRepository implements HandlerRepository {

        private final Map<Object, Object> handlers = new LinkedHashMap<>();

        @Override
        public Collection<? extends Entry<?>> findByAssociation(Map<Object, String> associations) {
            return List.of();
        }

        @Override
        public Collection<? extends Entry<?>> getAll() {
            return List.of();
        }

        @Override
        public Entry<?> get(Object id) {
            return null;
        }

        @Override
        public CompletableFuture<?> put(Object id, Object value) {
            return null;
        }

        @Override
        public CompletableFuture<?> delete(Object id) {
            return null;
        }

        @Override
        public CompletableFuture<?> put(Map<Object, Object> handlersById) {
            return null;
        }
    }
}
