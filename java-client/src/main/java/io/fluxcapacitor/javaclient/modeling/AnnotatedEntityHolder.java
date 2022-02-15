package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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
import static java.util.Optional.ofNullable;

@Slf4j
public class AnnotatedEntityHolder implements Entity.Holder {
    private static final Map<AccessibleObject, Entity.Holder> cache = new ConcurrentHashMap<>();
    private static final Pattern getterPattern = Pattern.compile("(get|is)([A-Z].*)");

    private final AccessibleObject location;
    private final Class<?> ownerType;
    private final BiFunction<Object, Object, Object> wither;
    private final Class<?> holderType;
    private final Function<Object, Id> idProvider;
    private final Class<?> entityType;

    private final EventSourcingHandler<?> eventSourcingHandler;
    private final Serializer serializer;

    public static Entity.Holder getEntityHolder(Class<?> ownerType, AccessibleObject location,
                                                EventSourcingHandler<?> eventSourcingHandler,
                                                Serializer serializer) {
        return cache.computeIfAbsent(location,
                                     l -> new AnnotatedEntityHolder(ownerType, l, eventSourcingHandler, serializer));
    }

    private AnnotatedEntityHolder(Class<?> ownerType, AccessibleObject location,
                                  EventSourcingHandler<?> eventSourcingHandler, Serializer serializer) {
        this.eventSourcingHandler = eventSourcingHandler;
        this.serializer = serializer;
        this.location = location;
        this.ownerType = ownerType;
        this.holderType = ReflectionUtils.getPropertyType(location);
        this.entityType = getCollectionElementType(location).orElse(holderType);
        Member member = location.getAnnotation(Member.class);
        String pathToId = member.idProperty();
        this.idProvider = pathToId.isBlank() ?
                v -> getAnnotatedProperty(v, EntityId.class).map(
                                p -> new Id(ofNullable(getValue(p, v)).orElse(null), getName(p)))
                        .orElseGet(() -> {
                            if (v instanceof Class<?>) {
                                return new Id(null, getAnnotatedProperty((Class<?>) v, EntityId.class)
                                        .map(ReflectionUtils::getName).orElse(null));
                            }
                            return new Id(null, null);
                        }) :
                v -> new Id(readProperty(pathToId, v).orElse(null), pathToId);
        String propertyName = Optional.of(getName(location)).map(name -> Optional.of(getterPattern.matcher(name)).map(
                matcher -> matcher.matches() ? matcher.group(2) : name).orElse(name)).orElseThrow().toLowerCase();
        Class<?>[] witherParams = new Class<?>[]{ReflectionUtils.getPropertyType(location)};
        Stream<Method> witherCandidates = ReflectionUtils.getAllMethods(this.ownerType).stream().filter(
                m -> m.getReturnType().isAssignableFrom(this.ownerType) || m.getReturnType().equals(void.class));
        witherCandidates = member.wither().isBlank() ?
                witherCandidates.filter(m -> Arrays.equals(witherParams, m.getParameterTypes())
                                             && m.getName().toLowerCase().contains(propertyName)) :
                witherCandidates.filter(m -> Objects.equals(member.wither(), m.getName()));
        this.wither = witherCandidates.findFirst().<BiFunction<Object, Object, Object>>map(
                m -> (o, h) -> safelyCall(() -> m.invoke(o, h))).orElseGet(() -> {
            AtomicBoolean warnedAboutMissingWither = new AtomicBoolean();
            return (o, h) -> {
                if (warnedAboutMissingWither.compareAndSet(false, true)) {
                    log.warn("No update function found for @Member {}. "
                             + "Updates to enclosed entities won't automatically update the parent entity.", location);
                }
                return o;
            };
        });

    }

    @Override
    public Stream<Entity<?, ?>> getEntities(Object owner) {
        Object holderValue = getValue(location, owner);
        if (holderValue instanceof Collection<?>) {
            return Stream.concat(
                    ((Collection<?>) holderValue).stream().flatMap(v -> createEntity(v, idProvider).stream()),
                    Stream.of(createEmptyEntity()));
        } else if (holderValue instanceof Map<?, ?>) {
            return Stream.concat(
                    ((Map<?, ?>) holderValue).entrySet().stream().flatMap(e -> createEntity(
                            e.getValue(), v -> new Id(e.getKey(), idProvider.apply(v).property())).stream()),
                    Stream.of(createEmptyEntity()));
        } else {
            return createEntity(holderValue, idProvider).or(() -> Optional.of(createEmptyEntity())).stream();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Entity<?, ?>> createEntity(Object member, Function<Object, Id> idProvider) {
        return Optional.ofNullable(member)
                .map(m -> idProvider.apply(member))
                .map(id -> ImmutableEntity.builder()
                        .value(member)
                        .type((Class) member.getClass())
                        .eventSourcingHandler(eventSourcingHandler.forType(member.getClass()))
                        .serializer(serializer)
                        .id(id.value())
                        .holder(this)
                        .idProperty(id.property())
                        .build());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Entity<?, ?> createEmptyEntity() {
        return ImmutableEntity.builder()
                .type((Class) entityType)
                .eventSourcingHandler(eventSourcingHandler.forType(entityType))
                .serializer(serializer)
                .holder(this)
                .idProperty(idProvider.apply(entityType).property())
                .build();
    }

    @SneakyThrows
    @Override
    public Object updateOwner(Object owner, Entity<?, ?> before, Entity<?, ?> after) {
        Object holder = ReflectionUtils.getValue(location, owner);
        if (Collection.class.isAssignableFrom(holderType)) {
            Collection<Object> collection = copyCollection(holder);
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
            Map<Object, Object> map = copyMap(holder);
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


    @SuppressWarnings("unchecked")
    private Map<Object, Object> copyMap(Object holder) {
        if (SortedMap.class.isAssignableFrom(holderType)) {
            return holder == null ? new TreeMap<>() : new TreeMap<>((Map<Object, ?>) holder);
        }
        return holder == null ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<Object, ?>) holder);
    }

    private Collection<Object> copyCollection(Object holder) {
        if (SortedSet.class.isAssignableFrom(holderType)) {
            return holder == null ? new TreeSet<>() : new TreeSet<>((Collection<?>) holder);
        }
        if (Set.class.isAssignableFrom(holderType)) {
            return holder == null ? new LinkedHashSet<>() : new LinkedHashSet<>((Collection<?>) holder);
        }
        return holder == null ? new ArrayList<>() : new ArrayList<>((Collection<?>) holder);
    }

    @Value
    @Accessors(fluent = true)
    private static class Id {
        Object value;
        String property;
    }

}
