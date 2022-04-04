package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.stream.Collectors.toList;

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
    transient AnnotatedEntityHolder holder;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient EventSourcingHandlerFactory handlerFactory;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Serializer serializer;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<? extends Entity<?, ?>> entities = computeEntities();

    @SuppressWarnings("unchecked")
    public Class<T> type() {
        return value == null ? type : (Class<T>) value.getClass();
    }

    @Override
    public T get() {
        return value;
    }

    private Collection<? extends ImmutableEntity<?>> computeEntities() {
        Class<?> type = value == null ? type() : value.getClass();
        List<ImmutableEntity<?>> result = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(type, Member.class)) {
            result.addAll(getEntityHolder(type, location, handlerFactory, serializer)
                                  .getEntities(this).collect(toList()));
        }
        return result;
    }

    @Override
    public <E extends Exception> ImmutableEntity<T> assertLegal(Object command) throws E {
        ValidationUtils.assertLegal(command, this);
        return this;
    }

    @Override
    public ImmutableEntity<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    @SuppressWarnings("unchecked")
    ImmutableEntity<T> apply(DeserializingMessage message) {
        EventSourcingHandler<T> handler = handlerFactory.forType(type());
        ImmutableEntity<T> result = handler.canHandle(this, message)
                ? toBuilder().value(handler.invoke(this, message)).build() : this;
        Object payload = message.getPayload();
        for (Entity<?, ?> entity : result.possibleTargets(payload)) {
            ImmutableEntity<?> immutableEntity = (ImmutableEntity<?>) entity;
            Entity<?, ?> updated = immutableEntity.apply(message);
            if (immutableEntity.get() != updated.get()) {
                result = result.toBuilder().value((T) immutableEntity
                        .holder().updateOwner(result.get(), entity, updated)).build();
            }
        }
        return result;
    }
}
