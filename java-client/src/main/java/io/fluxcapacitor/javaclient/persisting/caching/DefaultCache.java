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

package io.fluxcapacitor.javaclient.persisting.caching;


import io.fluxcapacitor.common.Registration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.manual;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.memoryPressure;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.size;

@AllArgsConstructor
@Slf4j
public class DefaultCache implements Cache {
    protected static final String mutexPrecursor = "$DC$";

    final Map<Object, CacheReference> valueMap;

    private final Executor evictionNotifier;
    private final Collection<Consumer<CacheEvictionEvent>> evictionListeners = new CopyOnWriteArrayList<>();

    private final ExecutorService referencePurger = Executors.newSingleThreadExecutor(
            newThreadFactory("DefaultCache-referencePurger"));

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    public DefaultCache() {
        this(1_000_000);
    }

    public DefaultCache(int maxSize) {
        this(maxSize, Executors.newSingleThreadExecutor(newThreadFactory("DefaultCache-evictionNotifier")));
    }

    public DefaultCache(int maxSize, Executor evictionNotifier) {
        this.valueMap = Collections.synchronizedMap(new LinkedHashMap<>(Math.min(128, maxSize), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, CacheReference> eldest) {
                boolean remove = size() > maxSize;
                try {
                    return remove;
                } finally {
                    if (remove) {
                        notifyEvictionListeners(eldest.getKey(), size);
                    }
                }
            }
        });
        this.evictionNotifier = evictionNotifier;
        this.referencePurger.execute(this::pollReferenceQueue);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        synchronized ((mutexPrecursor + id).intern()) {
            CacheReference previous = valueMap.get(id);
            CacheReference next = wrap(id, mappingFunction.apply(id, unwrap(previous)));
            if (next == null) {
                valueMap.remove(id);
                if (previous != null && previous.get() != null) {
                    notifyEvictionListeners(id, manual);
                }
            } else {
                valueMap.put(id, next);
            }
            return unwrap(next);
        }
    }

    @Override
    public Object put(Object id, Object value) {
        return compute(id, (k, v) -> value == null ? Optional.empty() : value);
    }

    @Override
    public Object putIfAbsent(Object id, Object value) {
        return computeIfAbsent(id, k -> value == null ? Optional.empty() : value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return compute(id, (k, v) -> v == null ? mappingFunction.apply(k) : v);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return compute(id, (k, v) -> v == null ? null : mappingFunction.apply(k, v));
    }

    @Override
    public <T> T remove(Object id) {
        return compute(id, (k, v) -> null);
    }

    @Override
    public <T> T get(Object id) {
        return unwrap(valueMap.get(id));
    }

    @Override
    public boolean containsKey(Object id) {
        return valueMap.containsKey(id);
    }

    @Override
    public void clear() {
        valueMap.clear();
        notifyEvictionListeners(null, manual);
    }

    @Override
    public int size() {
        return valueMap.size();
    }

    @Override
    public Registration registerEvictionListener(Consumer<CacheEvictionEvent> listener) {
        evictionListeners.add(listener);
        return () -> evictionListeners.remove(listener);
    }

    @Override
    public void close() {
        try {
            if (evictionNotifier instanceof ExecutorService executorService) {
                executorService.shutdownNow();
            }
            referencePurger.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    protected CacheReference wrap(Object id, Object value) {
        return value == null ? null : new CacheReference(id, value);
    }

    @SuppressWarnings("unchecked")
    protected <T> T unwrap(CacheReference ref) {
        if (ref == null) {
            return null;
        }
        Object result = ref.get();
        if (result instanceof Optional<?>) {
            result = ((Optional<?>) result).orElse(null);
        }
        return (T) result;
    }

    protected void pollReferenceQueue() {
        try {
            Reference<?> reference;
            while ((reference = referenceQueue.remove()) != null) {
                if (reference instanceof CacheReference cacheReference) {
                    remove(cacheReference.id);
                    notifyEvictionListeners(cacheReference.id, memoryPressure);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void notifyEvictionListeners(Object id, CacheEvictionEvent.Reason reason) {
        var event = new CacheEvictionEvent(id, reason);
        evictionNotifier.execute(() -> evictionListeners.forEach(l -> l.accept(event)));
    }

    protected class CacheReference extends SoftReference<Object> {
        private final Object id;

        public CacheReference(Object id, Object value) {
            super(value, referenceQueue);
            this.id = id;
        }
    }
}
