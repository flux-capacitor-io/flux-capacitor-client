package io.fluxcapacitor.javaclient.modeling;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.Instant;
import java.util.Collection;

@ToString
public abstract class DelegatingEntity<T> implements Entity<T> {
    @ToString.Include
    @EqualsAndHashCode.Include
    @Getter
    protected Entity<T> delegate;

    public DelegatingEntity(@NonNull Entity<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public Collection<?> aliases() {
        return delegate.aliases();
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
    public Entity<T> previous() {
        return delegate.previous();
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return delegate.entities();
    }

    @Override
    public Entity<?> parent() {
        return delegate.parent();
    }
}
