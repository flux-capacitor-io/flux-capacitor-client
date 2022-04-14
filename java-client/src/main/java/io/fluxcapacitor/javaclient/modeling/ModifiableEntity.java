package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Collection;
import java.util.stream.Collectors;

@Value
public class ModifiableEntity<T> implements Entity<ModifiableEntity<T>, T> {
    Entity<?, T> delegate;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ModifiableAggregateRoot<?> root;

    @Override
    public Collection<? extends Entity<?, ?>> entities() {
        return delegate.entities().stream().map(e -> new ModifiableEntity<>(e, root)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public ModifiableEntity<T> apply(Message eventMessage) {
        return (ModifiableEntity<T>) root.apply(eventMessage).getEntity(id()).orElse(null);
    }

    @Override
    public <E extends Exception> ModifiableEntity<T> assertLegal(Object payload) throws E {
        root.assertLegal(payload);
        return this;
    }

    @Override
    public Entity<?, ?> parent() {
        Entity<?, ?> parent = delegate.parent();
        return parent == null ? root : new ModifiableEntity<>(parent, root);
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
