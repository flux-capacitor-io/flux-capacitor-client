package io.fluxcapacitor.javaclient.persisting.caching;


import io.fluxcapacitor.common.Registration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.Collection;
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

import static io.fluxcapacitor.javaclient.persisting.caching.Cache.EvictionEvent.Reason.manual;
import static io.fluxcapacitor.javaclient.persisting.caching.Cache.EvictionEvent.Reason.memoryPressure;
import static io.fluxcapacitor.javaclient.persisting.caching.Cache.EvictionEvent.Reason.size;

@AllArgsConstructor
@Slf4j
public class DefaultCache implements Cache {
    protected static final String mutexPrecursor = "$DC$";

    final Map<Object, SoftReference<Object>> valueMap;

    private final Executor evictionNotifier;
    private final Collection<Consumer<EvictionEvent>> evictionListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService referencePurger = Executors.newSingleThreadScheduledExecutor();

    public DefaultCache() {
        this(1_000);
    }

    public DefaultCache(int maxSize) {
        this(maxSize, Duration.ofSeconds(60));
    }

    public DefaultCache(int maxSize, Duration purgeDelay) {
        this(maxSize, purgeDelay, Executors.newSingleThreadExecutor());
    }

    public DefaultCache(int maxSize, Duration purgeDelay, Executor evictionNotifier) {
        this.valueMap = new LinkedHashMap<>(Math.min(128, maxSize), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, SoftReference<Object>> eldest) {
                boolean remove = size() > maxSize;
                try {
                    return remove;
                } finally {
                    if (remove) {
                        DefaultCache.this.publishEvictionEvent(eldest.getKey(), size);
                    }
                }
            }
        };
        this.evictionNotifier = evictionNotifier;
        referencePurger.scheduleWithFixedDelay(this::purgeEmptyReferences, purgeDelay.toMillis(), purgeDelay.toMillis(),
                                               TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        synchronized ((mutexPrecursor + id).intern()) {
            SoftReference<Object> previous = valueMap.get(id);
            SoftReference<Object> next = wrap(mappingFunction.apply(id, unwrap(previous)));
            if (next == null) {
                valueMap.remove(id);
                if (previous != null) {
                    publishEvictionEvent(id, manual);
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
    }

    @Override
    public int size() {
        return valueMap.size();
    }

    @Override
    public Registration registerEvictionListener(Consumer<EvictionEvent> listener) {
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

    protected SoftReference<Object> wrap(Object value) {
        return value == null ? null : new SoftReference<>(value);
    }

    @SuppressWarnings("unchecked")
    protected <T> T unwrap(SoftReference<Object> ref) {
        if (ref == null) {
            return null;
        }
        Object result = ref.get();
        if (result instanceof Optional<?>) {
            result = ((Optional<?>) result).orElse(null);
        }
        return (T) result;
    }

    protected void purgeEmptyReferences() {
        try {
            valueMap.entrySet().removeIf(e -> {
                boolean remove = e.getValue().get() == null;
                try {
                    return remove;
                } finally {
                    if (remove) {
                        publishEvictionEvent(e.getKey(), memoryPressure);
                    }
                }
            });
        } catch (Throwable e) {
            log.warn("Failed to evict empty references. This warning can probably be ignored.", e);
        }
    }

    protected void publishEvictionEvent(Object id, EvictionEvent.Reason reason) {
        var event = new EvictionEvent(id, reason);
        evictionNotifier.execute(() -> evictionListeners.forEach(l -> l.accept(event)));
    }
}
