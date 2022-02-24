package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.stream.Collectors.groupingBy;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
@Slf4j
public class ImmutableEntity<T> implements Entity<ImmutableEntity<T>, T> {
    @JsonProperty
    Object id;
    @JsonProperty
    Class<T> type;
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    T value;
    @JsonProperty
    String idProperty;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Holder holder;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient EventSourcingHandlerFactory handlerFactory;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Serializer serializer;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<Entity<?, ?>> entities = computeEntities();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<Entity<?, ?>> allEntities = Entity.super.allEntities();

    @SuppressWarnings("unchecked")
    public Class<T> type() {
        return value == null ? type : (Class<T>) value.getClass();
    }

    @Override
    public T get() {
        return value;
    }

    private Collection<Entity<?, ?>> computeEntities() {
        Class<?> type = value == null ? type() : value.getClass();
        return getAnnotatedProperties(type, Member.class).stream().flatMap(
                location -> getEntityHolder(type, location, handlerFactory, serializer)
                        .getEntities(value)).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public ImmutableEntity<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    @Override
    public ImmutableEntity<T> apply(Object event) {
        if (event instanceof DeserializingMessage) {
            return apply(((DeserializingMessage) event));
        }
        return apply(asMessage(event));
    }

    @Override
    public ImmutableEntity<T> update(UnaryOperator<T> function) {
        return toBuilder().value(function.apply(get())).build();
    }


    @SuppressWarnings("unchecked")
    public ImmutableEntity<T> apply(DeserializingMessage message) {
        ImmutableEntity<T> result = toBuilder().value(handlerFactory.<T>forType(type()).invoke(this, message)).build();
        Object payload = message.getPayload();
        Iterator<Entity<?, ?>> iterator = result.possibleTargets(payload).iterator();
        while (iterator.hasNext()) {
            Entity<?, ?> entity = iterator.next();
            if (entity.isPossibleTarget(payload)) {
                Entity<?, ?> updated = entity.apply(message);
                if (!Objects.equals(updated, entity)) {
                    result = result.toBuilder().value((T) entity.holder().updateOwner(result.get(), entity, updated))
                            .build();
                }
            }
        }
        return result;
    }

    @Override
    public <E extends Exception> ImmutableEntity<T> assertLegal(Object... commands) throws E {
        if (commands.length > 0) {
            ImmutableEntity<T> result = this;
            Iterator<Object> iterator = Arrays.stream(commands).iterator();
            while (iterator.hasNext()) {
                Object c = iterator.next();
                ValidationUtils.assertLegal(c, result);
                possibleTargets(c).forEach(e -> e.assertLegal(c));
                if (iterator.hasNext()) {
                    result = result.apply(Message.asMessage(c));
                }
            }
        }
        return this;
    }

    Stream<Entity<?, ?>> possibleTargets(Object payload) {
        return entities().stream().collect(groupingBy(Entity::holder)).values().stream()
                .flatMap(group -> group.stream().filter(e -> e.isPossibleTarget(payload)).findFirst().stream());
    }
}
