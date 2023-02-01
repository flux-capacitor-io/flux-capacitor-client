package io.fluxcapacitor.javaclient.persisting.caching;


import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.AllArgsConstructor;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

@AllArgsConstructor
public class DefaultCache implements Cache {
    protected static final Object nullValue = new Object();

    private final ConcurrentHashMap<Object, CacheEntry> valueMap;
    private final ConcurrentSkipListMap<Long, Object> writeOrderMap;
    private final int maxSize;

    public DefaultCache() {
        this(1_000);
    }

    public DefaultCache(int maxSize) {
        this.maxSize = maxSize;
        this.valueMap = new ConcurrentHashMap<>();
        this.writeOrderMap = new ConcurrentSkipListMap<>();
    }

    @Override
    public Object put(Object id, Object value) {
        CacheEntry next = new CacheEntry(value == null ? nullValue : value);
        CacheEntry previous = valueMap.put(id, next);
        afterUpdate(id, previous, next);
        return unwrap(previous);
    }

    @Override
    public Object putIfAbsent(Object id, Object value) {
        return computeIfAbsent(id, k -> value == null ? nullValue : value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        AtomicReference<CacheEntry> previous = new AtomicReference<>();
        CacheEntry next = valueMap.compute(id, (k, v) -> {
            previous.set(v);
            if (unwrap(v) == null) {
                T nextValue = mappingFunction.apply(k);
                return nextValue == null ? null : new CacheEntry(nextValue);
            }
            return v;
        });
        afterUpdate(id, previous.get(), next);
        return unwrap(next);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        AtomicReference<CacheEntry> previous = new AtomicReference<>();
        CacheEntry next = valueMap.compute(id, (k, v) -> {
            previous.set(v);
            T current = unwrap(v);
            if (current == null) {
                return null;
            }
            T nextValue = mappingFunction.apply(k, current);
            return nextValue == null ? null : new CacheEntry(nextValue);
        });
        afterUpdate(id, previous.get(), next);
        return unwrap(next);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        AtomicReference<CacheEntry> previous = new AtomicReference<>();
        CacheEntry next = valueMap.compute(id, (k, v) -> {
            previous.set(v);
            T nextValue = mappingFunction.apply(k, unwrap(v));
            return nextValue == null ? null : new CacheEntry(nextValue);
        });
        afterUpdate(id, previous.get(), next);
        return unwrap(next);
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
    public <T> T remove(Object id) {
        var entry = valueMap.remove(id);
        if (entry != null) {
            writeOrderMap.remove(entry.writeIndex());
        }
        return unwrap(entry);
    }

    @Override
    public void clear() {
        writeOrderMap.clear();
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
                        valueMap.computeIfPresent(oldest.getValue(),
                                                  (k, v) -> v.writeIndex() == oldest.getKey() ? null : v);
                    }
                }
            }
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
