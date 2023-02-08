package io.fluxcapacitor.javaclient.persisting.caching;


import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.AllArgsConstructor;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

@AllArgsConstructor
public class DefaultCache implements Cache {
    protected static final Object nullValue = new Object();

    private final ConcurrentHashMap<Object, CacheEntry> valueMap;
    private final ConcurrentSkipListMap<Long, Object> writeOrderMap;
    private final ConcurrentHashMap<Object, Object> mutexMap;
    private final int maxSize;

    public DefaultCache() {
        this(1_000);
    }

    public DefaultCache(int maxSize) {
        this.maxSize = maxSize;
        this.valueMap = new ConcurrentHashMap<>();
        this.writeOrderMap = new ConcurrentSkipListMap<>();
        this.mutexMap = new ConcurrentHashMap<>();
    }

    @Override
    public Object put(Object id, Object value) {
        return compute(id, (k, v) -> value == null ? nullValue : value);
    }

    @Override
    public Object putIfAbsent(Object id, Object value) {
        return computeIfAbsent(id, k -> value == null ? nullValue : value);
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
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        synchronized (mutexMap.computeIfAbsent(id, k -> new Object())) {
            CacheEntry previous = valueMap.get(id);
            T nextValue = mappingFunction.apply(id, unwrap(previous));
            CacheEntry next = nextValue == null ? null : new CacheEntry(nextValue);
            if (nextValue == null) {
                valueMap.remove(id);
            } else {
                valueMap.put(id, next);
            }
            afterUpdate(id, previous, next);
            return unwrap(next);
        }
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
        writeOrderMap.clear();
        mutexMap.clear();
        valueMap.clear();
    }

    @Override
    public int size() {
        return valueMap.size();
    }

    protected void afterUpdate(Object id, CacheEntry previous, CacheEntry next) {
        if (previous == next) {
            return;
        }
        if (previous != null) {
            writeOrderMap.remove(previous.writeIndex());
        }
        if (next != null) {
            writeOrderMap.put(next.writeIndex(), id);
            if (previous == null) {
                if (size() > maxSize) {
                    var oldest = writeOrderMap.pollFirstEntry();
                    if (oldest != null) {
                        if (valueMap.computeIfPresent(
                                oldest.getValue(), (k, v) -> v.writeIndex() == oldest.getKey() ? null : v) == null) {
                            mutexMap.remove(id);
                        }
                    }
                }
            }
        } else {
            mutexMap.remove(id);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T unwrap(CacheEntry ref) {
        return ref == null ? null : (T) ref.get();
    }

    protected static class CacheEntry {
        protected static final AtomicLong currentWriteIndex = new AtomicLong(IndexUtils.indexForCurrentTime());

        private final SoftReference<Object> reference;
        private final long writeIndex;

        public CacheEntry(Object value) {
            this.reference = new SoftReference<>(value);
            this.writeIndex = currentWriteIndex.updateAndGet(IndexUtils::nextIndex);
        }

        public Object get() {
            Object result = reference.get();
            return result == nullValue ? null : result;
        }

        public long writeIndex() {
            return writeIndex;
        }
    }
}
