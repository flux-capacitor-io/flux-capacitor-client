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
import io.fluxcapacitor.javaclient.FluxCapacitor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.manual;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.memoryPressure;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionEvent.Reason.size;

/**
 * Default implementation of the {@link Cache} interface using key-level synchronized access and soft references for
 * value storage.
 * <p>
 * This cache is optimized for concurrent environments and provides built-in support for:
 * <ul>
 *     <li><strong>Automatic eviction</strong> based on max size (LRU policy)</li>
 *     <li><strong>Reference-based eviction</strong> when values are no longer strongly reachable (via {@link SoftReference})</li>
 *     <li><strong>Expiration</strong> after a configurable duration</li>
 *     <li><strong>Eviction notification</strong> via registered {@link CacheEvictionEvent} listeners</li>
 * </ul>
 *
 * <p>
 * The cache ensures thread safety through synchronized access on its internal {@link LinkedHashMap} and per-key locking
 * using {@code intern()} on a string prefix plus key combination. While this does introduce some memory overhead due to
 * string interning, it ensures atomic updates for concurrent access to the same key.
 *
 * <p><strong>Threading:</strong> Eviction listeners and expiration polling run on background threads.
 * The {@code valueMap} itself is backed by a synchronized {@link LinkedHashMap} with LRU eviction behavior.</p>
 *
 * @see Cache
 * @see CacheEvictionEvent
 */
@AllArgsConstructor
@Slf4j
public class DefaultCache implements Cache, AutoCloseable {
    protected static final String mutexPrecursor = "$DC$";

    @Getter
    private final Map<Object, CacheReference> valueMap;

    private final Executor evictionNotifier;
    private final Duration expiry;
    private final boolean softReferences;
    private final Clock clock;

    private final Collection<Consumer<CacheEvictionEvent>> evictionListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService referencePurger = Executors.newScheduledThreadPool(
            2, newThreadFactory("DefaultCache-referencePurger"));

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    /**
     * Constructs a cache with a default maximum size of 1,000,000 entries and no expiration.
     */
    public DefaultCache() {
        this(1_000_000);
    }

    /**
     * Constructs a cache with a specified max size and no expiration.
     */
    public DefaultCache(int maxSize) {
        this(maxSize, null);
    }

    /**
     * Constructs a cache with specified size and expiration. Uses a single-threaded executor for eviction
     * notifications.
     */
    public DefaultCache(int maxSize, Duration expiry) {
        this(maxSize, Executors.newSingleThreadExecutor(newThreadFactory("DefaultCache-evictionNotifier")), expiry);
    }

    /**
     * Constructs a cache with specified size, executor for eviction notifications and expiration.
     */
    public DefaultCache(int maxSize, Executor evictionNotifier, Duration expiry) {
        this(maxSize, evictionNotifier, expiry, Duration.ofMinutes(1), true);
    }

    /**
     * Constructs a cache with full configuration of size, eviction executor, expiration delay, and expiration check
     * frequency.
     */
    public DefaultCache(int maxSize, Executor evictionNotifier, Duration expiry, Duration expiryCheckDelay, boolean softReferences) {
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
        this.softReferences = softReferences;
        this.evictionNotifier = evictionNotifier;
        this.clock = FluxCapacitor.getOptionally().map(FluxCapacitor::clock).orElseGet(Clock::systemUTC);
        this.referencePurger.execute(this::pollReferenceQueue);
        if ((this.expiry = expiry) != null) {
            this.referencePurger.scheduleWithFixedDelay(
                    this::removeExpiredReferences, expiryCheckDelay.toMillis(), expiryCheckDelay.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Returns a synchronized computation that adds, removes, or updates a cache entry. Internally uses per-key
     * {@link String#intern()} synchronization to prevent race conditions.
     */
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
    public <T> void modifyEach(BiFunction<? super Object, ? super T, ? extends T> modifierFunction) {
        new HashSet<>(valueMap.keySet()).forEach(key -> computeIfPresent(key, modifierFunction));
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
        return value == null ? null : softReferences
                ? new SoftCacheReference(id, value) : new HardCacheReference(id, value);
    }

    @SuppressWarnings("unchecked")
    protected <T> T unwrap(CacheReference ref) {
        if (ref == null) {
            return null;
        }
        if (ref.hasExpired(clock)) {
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
                if (reference instanceof CacheReference r) {
                    remove(r.getId());
                    notifyEvictionListeners(r.getId(), memoryPressure);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void removeExpiredReferences() {
        valueMap.entrySet().removeIf(e -> {
            if (e.getValue().hasExpired(clock)) {
                notifyEvictionListeners(e.getKey(), CacheEvictionEvent.Reason.expiry);
                return true;
            }
            return false;
        });
    }

    protected void notifyEvictionListeners(Object id, CacheEvictionEvent.Reason reason) {
        var event = new CacheEvictionEvent(id, reason);
        evictionNotifier.execute(() -> evictionListeners.forEach(l -> l.accept(event)));
    }

    @Getter
    protected class SoftCacheReference extends SoftReference<Object> implements CacheReference {
        private final Object id;
        private final Instant deadline = expiry == null ? null : clock.instant().plus(expiry);

        public SoftCacheReference(Object id, Object value) {
            super(value, referenceQueue);
            this.id = id;
        }
    }

    @Value
    public class HardCacheReference implements CacheReference {
        Object id;
        Object value;
        Instant deadline = expiry == null ? null : clock.instant().plus(expiry);

        @Override
        public Object get() {
            return value;
        }
    }

    protected interface CacheReference {
        Object getId();

        Object get();

        Instant getDeadline();

        default boolean hasExpired(Clock clock) {
            return getDeadline() != null && getDeadline().isBefore(clock.instant());
        }
    }
}
