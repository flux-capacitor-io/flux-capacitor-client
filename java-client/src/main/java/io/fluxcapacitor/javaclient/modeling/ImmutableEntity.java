package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.hasProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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
    Iterable<? extends Entity<?, ?>> entities = computeEntities();

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

    private Iterable<? extends Entity<?, ?>> computeEntities() {
        Class<?> type = value == null ? type() : value.getClass();
        List<ImmutableEntity<?>> result = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(type, Member.class)) {
            result.addAll(getEntityHolder(type, location, handlerFactory, serializer).getEntities(value).collect(
                    Collectors.toList()));
        }
        return result;
    }

    @Override
    public ImmutableEntity<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    @Override
    public ImmutableEntity<T> update(UnaryOperator<T> function) {
        return toBuilder().value(function.apply(get())).build();
    }

    @SuppressWarnings("unchecked")
    public ImmutableEntity<T> apply(DeserializingMessage message) {
        EventSourcingHandler<T> handler = handlerFactory.forType(type());
        ImmutableEntity<T> result = handler.canHandle(this, message)
                ? toBuilder().value(handler.invoke(this, message)).build() : this;
        Object payload = message.getPayload();
        for (ImmutableEntity<?> entity : result.possibleTargets(payload)) {
            ImmutableEntity<?> updated = entity.apply(message);
            if (entity.get() != updated.get()) {
                result = result.toBuilder().value(
                        (T) entity.holder().updateOwner(result.get(), entity, updated)).build();
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

    Iterable<ImmutableEntity<?>> possibleTargets(Object payload) {
        for (Entity<?, ?> e : entities()) {
            if (e.isPossibleTarget(payload)) {
                return singletonList((ImmutableEntity<?>) e);
            }
        }
        return emptyList();
    }

    @Override
    public boolean isPossibleTarget(Object message) {
        if (message == null) {
            return false;
        }
        for (Entity<?, ?> e : entities()) {
            if (e.isPossibleTarget(message)) {
                return true;
            }
        }
        String idProperty = idProperty();
        Object id = id();
        if (idProperty == null) {
            return true;
        }
        if (id == null && get() != null) {
            return false;
        }
        Object payload = message instanceof Message ? ((Message) message).getPayload() : message;
        if (id == null) {
            return hasProperty(idProperty, payload);
        }
        return readProperty(idProperty, payload)
                .or(() -> getAnnotatedPropertyValue(payload, RoutingKey.class)).map(id::equals).orElse(false);
    }
}
