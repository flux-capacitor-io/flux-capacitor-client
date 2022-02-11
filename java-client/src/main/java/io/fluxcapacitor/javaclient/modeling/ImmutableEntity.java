package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
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

import java.lang.reflect.AccessibleObject;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getCollectionElementType;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getName;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPropertyType;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Optional.ofNullable;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
@Slf4j
public class ImmutableEntity<T> implements Entity<ImmutableEntity<T>, T> {
    @JsonProperty
    String id;
    @JsonProperty
    Class<T> type;
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    T value;
    @JsonProperty
    String idProperty;

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

    public static <T> ImmutableEntity<T> from(ImmutableEntity<T> a, EventSourcingHandler<T> eventSourcingHandler,
                                              Serializer serializer) {
        if (a == null) {
            return null;
        }
        return ImmutableEntity.<T>builder()
                .id(a.id())
                .type(a.type())
                .value(a.get())
                .serializer(serializer)
                .eventSourcingHandler(eventSourcingHandler)
                .build();
    }

    @Override
    public ImmutableEntity<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    public ImmutableEntity<T> apply(DeserializingMessage message) {
        return toBuilder().value(eventSourcingHandler.invoke(this, message)).build();
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

    @Override
    public T get() {
        return value;
    }

    private Collection<Entity<?, ?>> computeEntities() {
        return value == null ? Collections.emptySet() : unmodifiableCollection(
                getAnnotatedProperties(value.getClass(), Member.class).stream().flatMap(this::getEntities)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private Stream<Entity<?, ?>> getEntities(AccessibleObject location) {
        Object member = getValue(location, value);
        Function<Object, Id> idProvider = idProvider(location);
        if (member instanceof Collection<?>) {
            return Stream.concat(((Collection<?>) member).stream().flatMap(v -> createEntity(v, idProvider).stream()),
                                 Stream.of(createEmptyEntity(getCollectionElementType(location), idProvider)));
        } else if (member instanceof Map<?, ?>) {
            return Stream.concat(((Map<?, ?>) member).entrySet().stream().flatMap(
                    e -> createEntity(e.getValue(), v -> new Id(
                            e.getKey().toString(), idProvider.apply(v).property())).stream()),
                                 Stream.of(createEmptyEntity(getCollectionElementType(location), idProvider)));
        } else {
            return createEntity(member, idProvider).or(
                    () -> Optional.of(createEmptyEntity(getPropertyType(location), idProvider))).stream();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Entity<?, ?>> createEntity(Object member, Function<Object, Id> idProvider) {
        if (member == null) {
            return Optional.empty();
        }
        return Optional.of(idProvider.apply(member)).map(id -> ImmutableEntity.builder()
                .value(member)
                .type((Class) member.getClass())
                .eventSourcingHandler(eventSourcingHandler.forType(member.getClass()))
                .serializer(serializer)
                .id(id.value())
                .idProperty(id.property())
                .build());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Entity<?, ?> createEmptyEntity(Class<?> type, Function<Object, Id> idProvider) {
        return ImmutableEntity.builder()
                .type((Class) type)
                .eventSourcingHandler(eventSourcingHandler.forType(type))
                .serializer(serializer)
                .idProperty(idProvider.apply(type).property())
                .build();
    }

    private static Function<Object, Id> idProvider(AccessibleObject property) {
        Member member = property.getAnnotation(Member.class);
        String pathToId = member.idProperty();
        return pathToId.isBlank() ?
                v -> getAnnotatedProperty(v, EntityId.class).map(
                                p -> new Id(ofNullable(getValue(p, v)).map(Objects::toString).orElse(null), getName(p)))
                        .orElseGet(() -> {
                            if (v instanceof Class<?>) {
                                return new Id(null, getAnnotatedProperty((Class<?>) v, EntityId.class)
                                        .map(ReflectionUtils::getName).orElse(null));
                            }
                            return new Id(null, null);
                        }) :
                v -> new Id(readProperty(pathToId, v).map(Object::toString).orElse(null), pathToId);
    }

    @Value
    private static class Id {
        String value;
        String property;
    }
}
