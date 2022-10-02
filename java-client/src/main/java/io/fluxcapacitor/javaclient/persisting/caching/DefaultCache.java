package io.fluxcapacitor.javaclient.persisting.caching;


import lombok.AllArgsConstructor;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Collections.synchronizedMap;

@AllArgsConstructor
public class DefaultCache implements Cache {
    private final Map<Object, SoftReference<Object>> delegate;

    public DefaultCache() {
        this(1_000);
    }

    public DefaultCache(int maxSize) {
        this.delegate = synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, SoftReference<Object>> eldest) {
                return size() > maxSize;
            }
        });
    }

    @Override
    public Object put(Object id, Object value) {
        return unwrap(delegate.put(id, new SoftReference<>(value)));
    }

    @Override
    public Object putIfAbsent(Object id, Object value) {
        return computeIfAbsent(id, k -> value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return unwrap(delegate.compute(id, (k, v) -> unwrap(v) == null ? new SoftReference<>(mappingFunction.apply(k)) : v));
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return unwrap(
                delegate.compute(id, (k, v) -> {
                    T current = unwrap(v);
                    return current == null ? null : new SoftReference<>(mappingFunction.apply(k, current));
                }));
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return unwrap(delegate.compute(id, (k, v) -> new SoftReference<>(mappingFunction.apply(k, unwrap(v)))));
    }

    @Override
    public <T> T getIfPresent(Object id) {
        return unwrap(delegate.get(id));
    }

    @Override
    public void invalidate(Object id) {
        delegate.remove(id);
    }

    @Override
    public void invalidateAll() {
        delegate.clear();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @SuppressWarnings("unchecked")
    protected <T> T unwrap(SoftReference<Object> ref) {
        return ref == null ? null : (T) ref.get();
    }
}
