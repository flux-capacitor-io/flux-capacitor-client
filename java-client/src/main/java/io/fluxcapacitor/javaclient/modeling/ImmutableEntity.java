package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.Collections.unmodifiableCollection;

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
    transient EventSourcingHandler<T> eventSourcingHandler;

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

    @Override
    public T get() {
        return value;
    }

    private Collection<Entity<?, ?>> computeEntities() {
        return value == null ? Collections.emptySet() : unmodifiableCollection(
                getAnnotatedProperties(value.getClass(), Member.class).stream().flatMap(
                        location -> getEntityHolder(value.getClass(), location, eventSourcingHandler, serializer)
                                .getEntities(value)).collect(Collectors.toCollection(LinkedHashSet::new)));
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
        ImmutableEntity<T> result = holder == null || eventSourcingHandler.canHandle(this, message)
                ? toBuilder().value(eventSourcingHandler.invoke(this, message)).build() : this;
        Object payload = message.getPayload();
        Collection<Entity<?, ?>> entities = result.entities();
        for (Entity<?, ?> entity : entities) {
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
}
