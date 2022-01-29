package io.fluxcapacitor.javaclient.persisting.caching;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@AllArgsConstructor
public class NamedCache implements Cache {
    private final Cache delegate;
    private final UnaryOperator<String> idFunction;

    @Override
    public void put(String id, @NonNull Object value) {
        delegate.put(idFunction.apply(id), value);
    }

    @Override
    public void putIfAbsent(String id, @NonNull Object value) {
        delegate.putIfAbsent(idFunction.apply(id), value);
    }

    @Override
    public <T> T computeIfAbsent(String id, Function<? super String, T> mappingFunction) {
        return delegate.computeIfAbsent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T computeIfPresent(String id, BiFunction<? super String, ? super T, ? extends T> mappingFunction) {
        return delegate.computeIfPresent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T compute(String id, BiFunction<? super String, ? super T, ? extends T> mappingFunction) {
        return delegate.compute(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T getIfPresent(String id) {
        return delegate.getIfPresent(idFunction.apply(id));
    }

    @Override
    public void invalidate(String id) {
        delegate.invalidate(idFunction.apply(id));
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
    }
}
