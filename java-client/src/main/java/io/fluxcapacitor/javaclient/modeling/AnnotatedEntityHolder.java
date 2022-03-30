package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.safelyCall;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getCollectionElementType;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getName;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

@Slf4j
public class AnnotatedEntityHolder {
    private static final Map<AccessibleObject, AnnotatedEntityHolder> cache = new ConcurrentHashMap<>();
    private static final Pattern getterPattern = Pattern.compile("(get|is)([A-Z].*)");

    private final AccessibleObject location;
    private final Class<?> ownerType;
    private final BiFunction<Object, Object, Object> wither;
    private final Class<?> holderType;
    private final Function<Object, Id> idProvider;
    private final Class<?> entityType;

    private final EventSourcingHandlerFactory handlerFactory;
    private final Serializer serializer;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Getter(lazy = true)
    private final ImmutableEntity<?> emptyEntity = ImmutableEntity.builder()
            .type((Class) entityType)
            .handlerFactory(handlerFactory)
            .serializer(serializer)
            .holder(this)
            .idProperty(idProvider.apply(entityType).property())
            .build();

    public static AnnotatedEntityHolder getEntityHolder(Class<?> ownerType, AccessibleObject location,
                                                        EventSourcingHandlerFactory handlerFactory,
                                                        Serializer serializer) {
        return cache.computeIfAbsent(location,
                                     l -> new AnnotatedEntityHolder(ownerType, l, handlerFactory, serializer));
    }

    private AnnotatedEntityHolder(Class<?> ownerType, AccessibleObject location,
                                  EventSourcingHandlerFactory handlerFactory, Serializer serializer) {
        this.handlerFactory = handlerFactory;
        this.serializer = serializer;
        this.location = location;
        this.ownerType = ownerType;
        this.holderType = ReflectionUtils.getPropertyType(location);
        this.entityType = getCollectionElementType(location).orElse(holderType);
        Member member = location.getAnnotation(Member.class);
        String pathToId = member.idProperty();
        this.idProvider = pathToId.isBlank() ?
                v -> getAnnotatedProperty(v, EntityId.class).map(
                                p -> new Id(ofNullable(getValue(p, v, false)).orElse(null), getName(p)))
                        .orElseGet(() -> {
                            if (v instanceof Class<?>) {
                                return new Id(null, getAnnotatedProperty((Class<?>) v, EntityId.class)
                                        .map(ReflectionUtils::getName).orElse(null));
                            }
                            return new Id(null, null);
                        }) :
                v -> new Id(readProperty(pathToId, v).orElse(null), pathToId);
        String propertyName = decapitalize(Optional.of(getName(location)).map(name -> Optional.of(
                        getterPattern.matcher(name)).map(matcher -> matcher.matches() ? matcher.group(2) : name)
                .orElse(name)).orElseThrow());
        Class<?>[] witherParams = new Class<?>[]{ReflectionUtils.getPropertyType(location)};
        Stream<Method> witherCandidates = ReflectionUtils.getAllMethods(this.ownerType).stream().filter(
                m -> m.getReturnType().isAssignableFrom(this.ownerType) || m.getReturnType().equals(void.class));
        witherCandidates = member.wither().isBlank() ?
                witherCandidates.filter(m -> Arrays.equals(witherParams, m.getParameterTypes())
                                             && m.getName().toLowerCase().contains(propertyName.toLowerCase())) :
                witherCandidates.filter(m -> Objects.equals(member.wither(), m.getName()));
        Optional<BiFunction<Object, Object, Object>> wither =
                witherCandidates.findFirst().map(m -> (o, h) -> safelyCall(() -> m.invoke(o, h)));
        this.wither = wither.orElseGet(() -> {
            AtomicBoolean warningIssued = new AtomicBoolean();
            Field field = ReflectionUtils.getField(ownerType, propertyName).orElse(null);
            return (o, h) -> {
                if (warningIssued.get()) {
                    return o;
                }
                if (field == null) {
                    if (warningIssued.compareAndSet(false, true)) {
                        log.warn("No update function found for @Member {}. "
                                 + "Updates to enclosed entities won't automatically update the parent entity.",
                                 location);
                    }
                } else {
                    try {
                        o = serializer.clone(o);
                        ReflectionUtils.setField(field, o, h);
                    } catch (Exception e) {
                        if (warningIssued.compareAndSet(false, true)) {
                            log.warn("Not able to update @Member {}. Please add a wither or setter method.", location,
                                     e);
                        }
                    }
                }
                return o;
            };
        });

    }

    public Stream<? extends ImmutableEntity<?>> getEntities(Entity<?, ?> parent) {
        Object holderValue = getValue(location, parent.get(), false);
        Class<?> type = holderValue == null ? holderType : holderValue.getClass();
        if (holderValue == null) {
            return Stream.of(getEmptyEntity());
        }
        if (Collection.class.isAssignableFrom(type)) {
            return Stream.concat(
                    ((Collection<?>) holderValue).stream().map(v -> createEntity(v, idProvider, parent).orElse(null))
                            .filter(Objects::nonNull),
                    Stream.of(getEmptyEntity()));
        } else if (Map.class.isAssignableFrom(type)) {
            return Stream.concat(
                    ((Map<?, ?>) holderValue).entrySet().stream().flatMap(e -> createEntity(
                            e.getValue(), v -> new Id(e.getKey(), idProvider.apply(v).property()), parent).stream()),
                    Stream.of(getEmptyEntity()));
        } else {
            return createEntity(holderValue, idProvider, parent).stream();
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<ImmutableEntity<?>> createEntity(Object member, Function<Object, Id> idProvider,
                                                      Entity<?, ?> parent) {
        if (member == null) {
            return empty();
        }
        Id id = idProvider.apply(member);
        return id == null ? empty() : Optional.of(
                ImmutableEntity.builder()
                        .value(member)
                        .type((Class) member.getClass())
                        .handlerFactory(handlerFactory)
                        .serializer(serializer)
                        .id(id.value())
                        .holder(this)
                        .idProperty(id.property())
                        .parent(parent)
                        .build());
    }

    @SneakyThrows
    public Object updateOwner(Object owner, Entity<?, ?> before, Entity<?, ?> after) {
        Object holder = ReflectionUtils.getValue(location, owner);
        if (Collection.class.isAssignableFrom(holderType)) {
            Collection<Object> collection = serializer.clone(holder);
            if (collection instanceof List<?>) {
                List<Object> list = (List<Object>) collection;
                int index = list.indexOf(before.get());
                if (index < 0) {
                    list.add(after.get());
                } else {
                    if (after.get() == null) {
                        list.remove(index);
                    } else {
                        list.set(index, after.get());
                    }
                }
                holder = list;
            } else {
                collection.remove(before.get());
                collection.add(after.get());
                holder = collection;
            }
        } else if (Map.class.isAssignableFrom(holderType)) {
            Map<Object, Object> map = serializer.clone(holder);
            Object id = Optional.ofNullable(after.id()).orElseGet(() -> idProvider.apply(after.get()).value());
            if (after.get() == null) {
                map.remove(id);
            } else {
                map.put(id, after.get());
            }
            holder = map;
        } else {
            holder = after.get();
        }
        Object result = wither.apply(owner, holder);
        return result == null ? owner : result;
    }

    @Value
    @Accessors(fluent = true)
    private static class Id {
        Object value;
        String property;
    }

}
