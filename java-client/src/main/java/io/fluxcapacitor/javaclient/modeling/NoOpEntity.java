package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Accessors(fluent = true)
public class NoOpEntity<T> implements Entity<T> {

    private final Supplier<Entity<T>> loader;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Entity<T> delegate = loader.get();

    private NoOpEntity(Entity<T> delegate) {
        this.loader = () -> delegate;
    }

    @Override
    public NoOpEntity<T> apply(Message eventMessage) {
        return this;
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
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
    public Entity<?> parent() {
        return Optional.ofNullable(delegate().parent()).map(NoOpEntity::new).orElse(null);
    }

    @Override
    public Collection<?> aliases() {
        return emptyList();
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return delegate().entities().stream().map(e -> new NoOpEntity<>((Entity<?>) e)).collect(toList());
    }

    @Override
    public Entity<T> previous() {
        return new NoOpEntity<>(delegate().previous());
    }

    @Override
    public Object id() {
        return delegate().id();
    }

    @Override
    public Class<T> type() {
        return delegate().type();
    }

    @Override
    public T get() {
        return delegate().get();
    }

    @Override
    public String idProperty() {
        return delegate().idProperty();
    }
}
