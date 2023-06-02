package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getValue;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.Collections.emptyList;

@Value
@NonFinal
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@Slf4j
@AllArgsConstructor
public class ImmutableEntity<T> implements Entity<T> {
    @JsonProperty
    Object id;
    @JsonProperty
    Class<T> type;
    @ToString.Exclude
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    T value;
    @JsonProperty
    String idProperty;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Entity<?> parent;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient AnnotatedEntityHolder holder;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient EntityHelper entityHelper;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Serializer serializer;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<? extends Entity<?>> entities = computeEntities();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<?> aliases = computeAliases();

    @SuppressWarnings("unchecked")
    public Class<T> type() {
        return value == null ? type : (Class<T>) value.getClass();
    }

    @Override
    public T get() {
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        ImmutableEntity<T> after = toBuilder().value(function.apply(get())).build();
        if (parent == null) {
            return after;
        }
        return parent.update((UnaryOperator) p -> holder.updateOwner(p, this, after));
    }

    @Override
    public ImmutableEntity<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        entityHelper.assertLegal(command, root());
        return this;
    }

    @SuppressWarnings("unchecked")
    ImmutableEntity<T> apply(DeserializingMessage message) {
        Optional<HandlerInvoker> invoker = entityHelper.applyInvoker(message, this);
        if (invoker.isPresent()) {
            return toBuilder().value((T) invoker.get().invoke()).build();
        }
        ImmutableEntity<T> result = this;
        Object payload = message.getPayload();
        for (Entity<?> entity : result.possibleTargets(payload)) {
            ImmutableEntity<?> immutableEntity = (ImmutableEntity<?>) entity;
            Entity<?> updated = immutableEntity.apply(message);
            if (immutableEntity.get() != updated.get()) {
                result = result.toBuilder().value((T) immutableEntity
                        .holder().updateOwner(result.get(), entity, updated)).build();
            }
        }
        return result;
    }

    protected Collection<? extends ImmutableEntity<?>> computeEntities() {
        Class<?> type = value == null ? type() : value.getClass();
        List<ImmutableEntity<?>> result = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(type, Member.class)) {
            result.addAll(getEntityHolder(type, location, entityHelper, serializer)
                                  .getEntities(this).toList());
        }
        return result;
    }

    protected Collection<?> computeAliases() {
        Object target = get();
        if (target == null) {
            return emptyList();
        }
        List<Object> results = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(target.getClass(), Alias.class)) {
            Object v = getValue(location, target, false);
            if (v != null) {
                if (v instanceof Collection<?> collection) {
                    results.addAll(collection);
                } else {
                    results.add(v);
                }
            }
        }
        return results;
    }
}
