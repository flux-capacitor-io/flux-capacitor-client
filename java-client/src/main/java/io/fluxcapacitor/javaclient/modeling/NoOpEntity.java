package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@AllArgsConstructor
@Accessors(fluent = true)
public class NoOpEntity<T> implements Entity<NoOpEntity<T>, T> {

    private final Entity<?, T> delegate;

    @Override
    public NoOpEntity<T> apply(Message eventMessage) {
        return this;
    }

    @Override
    public <E extends Exception> NoOpEntity<T> assertLegal(Object command) throws E {
        return this;
    }

    @Override
    public <E extends Exception> NoOpEntity<T> assertThat(Validator<T, E> validator) throws E {
        return this;
    }

    @Override
    public <E extends Exception> NoOpEntity<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        return this;
    }

    @Override
    public Entity<?, ?> parent() {
        Entity<?, ?> parent = delegate.parent();
        return parent instanceof AggregateRoot<?> ? parent : parent == null ? null : new NoOpEntity<>(parent);
    }

    @Override
    public Collection<? extends Entity<?, ?>> entities() {
        return delegate.entities().stream().map(NoOpEntity::new).collect(Collectors.toList());
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public Class<T> type() {
        return delegate.type();
    }

    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    public String idProperty() {
        return delegate.idProperty();
    }
}
