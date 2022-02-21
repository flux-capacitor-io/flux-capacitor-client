package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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

    @Override
    public <E extends Exception> AggregateRoot<T> assertLegal(Object... commands) throws E {
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
    public Holder holder() {
        return delegate().holder();
    }

    @Override
    public Collection<Entity<?, ?>> entities() {
        return delegate().entities();
    }
}
