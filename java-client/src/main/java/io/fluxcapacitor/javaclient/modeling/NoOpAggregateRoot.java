package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@AllArgsConstructor
@Accessors(fluent = true)
public class NoOpAggregateRoot<T> implements AggregateRoot<T> {

    private final Supplier<AggregateRoot<T>> loader;

    @Getter(lazy = true)
    private final AggregateRoot<T> delegate = loader.get();

    @Override
    public AggregateRoot<T> apply(Message eventMessage) {
        return this;
    }

    @Override
    public AggregateRoot<T> update(UnaryOperator<T> function) {
        return this;
    }

    /*
        Assertions are ignored
     */

    @Override
    public <E extends Exception> AggregateRoot<T> assertLegal(Object command) throws E {
        return this;
    }

    @Override
    public <E extends Exception> AggregateRoot<T> assertThat(Validator<T, E> validator) throws E {
        return this;
    }

    @Override
    public <E extends Exception> AggregateRoot<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        return this;
    }

    /*
        Getters are allowed though they require loading the aggregate
     */

    @Override
    public String lastEventId() {
        return delegate().lastEventId();
    }

    @Override
    public Long lastEventIndex() {
        return delegate().lastEventIndex();
    }

    @Override
    public Instant timestamp() {
        return delegate().timestamp();
    }

    @Override
    public long sequenceNumber() {
        return delegate().sequenceNumber();
    }

    @Override
    public AggregateRoot<T> previous() {
        return Optional.ofNullable(delegate().previous()).map(a -> new NoOpAggregateRoot<>(() -> a)).orElse(null);
    }

    @Override
    public Object id() {
        return delegate().type();
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

    @Override
    public Collection<? extends Entity<?, ?>> entities() {
        return delegate().entities().stream().map(NoOpEntity::new).collect(Collectors.toList());
    }

}
