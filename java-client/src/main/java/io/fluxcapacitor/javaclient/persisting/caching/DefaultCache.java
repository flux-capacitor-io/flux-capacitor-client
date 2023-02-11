package io.fluxcapacitor.javaclient.persisting.caching;


import lombok.AllArgsConstructor;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@AllArgsConstructor
public class DefaultCache implements Cache {
    protected static final String mutexPrecursor = "$DC$";

    private final Map<Object, SoftReference<Object>> valueMap;

    public DefaultCache() {
        this(1_000);
    }

    public DefaultCache(int maxSize) {
        this.valueMap = new LinkedHashMap<>(Math.min(128, maxSize), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, SoftReference<Object>> eldest) {
                return size() > maxSize;
            }
        };
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        synchronized ((mutexPrecursor + id).intern()) {
            SoftReference<Object> next = wrap(mappingFunction.apply(id, unwrap(valueMap.get(id))));
            if (next == null) {
                valueMap.remove(id);
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
}
