package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.Registration;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;
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
    public <T> T get(Object id) {
        return delegate.get(idFunction.apply(id));
    }

    @Override
    public boolean containsKey(Object id) {
        return delegate.containsKey(idFunction.apply(id));
    }

    @Override
    public <T> T remove(Object id) {
        return delegate.remove(idFunction.apply(id));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Registration registerEvictionListener(Consumer<EvictionEvent> listener) {
        return delegate.registerEvictionListener(listener);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
