package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Collection;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Value
@ToString(callSuper = true)
public class ModifiableEntity<T> extends DelegatingEntity<T> {
    public ModifiableEntity(Entity<T> delegate, ModifiableAggregateRoot<?> root) {
        super(delegate);
        this.root = root;
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ModifiableAggregateRoot<?> root;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        return (Entity<T>) ((Entity) root).update(
                r -> delegate.update(function).root().get()).getEntity(id()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Entity<T> apply(Message eventMessage) {
        return (Entity<T>) root.apply(eventMessage).getEntity(id()).orElse(null);
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return super.entities().stream().map(e -> new ModifiableEntity<>(e, root)).collect(Collectors.toList());
    }

    @Override
    public Entity<?> parent() {
        return ofNullable(super.parent()).map(entity -> new ModifiableEntity<>(entity, root)).orElse(null);
    }

    @Override
    public Entity<T> previous() {
        return ofNullable(super.previous()).map(e -> new ModifiableEntity<>(e, root)).orElse(null);
    }
}
