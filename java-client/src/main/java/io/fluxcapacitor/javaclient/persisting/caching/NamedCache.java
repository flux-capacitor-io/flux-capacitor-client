package io.fluxcapacitor.javaclient.persisting.caching;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@AllArgsConstructor
public class NamedCache implements Cache {
    private final Cache delegate;
    private final UnaryOperator<Object> idFunction;

    @Override
    public Object put(Object id, @NonNull Object value) {
        return delegate.put(idFunction.apply(id), value);
    }

    @Override
    public Object putIfAbsent(Object id, @NonNull Object value) {
        return delegate.putIfAbsent(idFunction.apply(id), value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return delegate.computeIfAbsent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return delegate.computeIfPresent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return delegate.compute(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T getIfPresent(Object id) {
        return delegate.getIfPresent(idFunction.apply(id));
    }

    @Override
    public void invalidate(Object id) {
        delegate.invalidate(idFunction.apply(id));
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
