package io.fluxcapacitor.javaclient.modeling;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.Instant;

public abstract class DelegatingAggregateRoot<T, A extends AggregateRoot<T>> implements AggregateRoot<T> {
    @ToString.Include
    @EqualsAndHashCode.Include
    @Getter
    protected A delegate;

    public DelegatingAggregateRoot(@NonNull A delegate) {
        this.delegate = delegate;
    }

    @Override
    public <E extends Exception> AggregateRoot<T> assertLegal(Object... commands) throws E {
        delegate.assertLegal(commands);
        return this;
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

    @Override
    public String lastEventId() {
        return delegate.lastEventId();
    }

    @Override
    public Long lastEventIndex() {
        return delegate.lastEventIndex();
    }

    @Override
    public Instant timestamp() {
        return delegate.timestamp();
    }

    @Override
    public long sequenceNumber() {
        return delegate.sequenceNumber();
    }

    @Override
    public AggregateRoot<T> previous() {
        return delegate.previous();
    }

    @Override
    public Iterable<? extends Entity<?, ?>> entities() {
        return delegate.entities();
    }

    @Override
    public boolean isPossibleTarget(Object message) {
        return delegate.isPossibleTarget(message);
    }
}
