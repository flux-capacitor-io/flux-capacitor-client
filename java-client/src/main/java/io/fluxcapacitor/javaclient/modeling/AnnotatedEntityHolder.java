/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.reflection.DefaultMemberInvoker;
import io.fluxcapacitor.common.reflection.MemberInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.Getter;
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

import static io.fluxcapacitor.common.ObjectUtils.call;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getCollectionElementType;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getName;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static java.beans.Introspector.decapitalize;
import static java.util.Optional.empty;

/**
 * Helper class for {@link Entity} instances that manage nested entity relationships annotated with
 * {@link Member @Member}.
 * <p>
 * The {@code AnnotatedEntityHolder} provides a way to access and update a composite object (holder) that contains one
 * or more nested entities. It supports collections, maps, and single-value member fields. It extracts member values
 * using reflection and allows updating them through generated "wither" or "setter" logic, making it possible to
 * reconstruct an updated parent entity when one of its children changes.
 * <p>
 * The holder is typically tied to a specific {@link AccessibleObject} (field or method), which is analyzed and cached
 * to optimize repeated operations.
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Extract and cache metadata about a member location (field/method), including its type, ID property, and wither.</li>
 *   <li>Create {@link ImmutableEntity} instances from nested values at this member location.</li>
 *   <li>Apply updates to these entities and propagate the changes back to the parent object via a wither or clone-and-set.</li>
 *   <li>Support updating collection and map-based member fields while preserving immutability semantics.</li>
 * </ul>
 *
 * <p>
 * The holder is initialized and cached via {@link #getEntityHolder(Class, AccessibleObject, EntityHelper, Serializer)},
 * and update logic falls back to serialization-based cloning if a wither method is not explicitly defined.
 *
 * @see ImmutableEntity
 * @see Member
 */
@Slf4j
public class AnnotatedEntityHolder {
    private static final Map<AccessibleObject, AnnotatedEntityHolder> cache = new ConcurrentHashMap<>();
    private static final Pattern getterPattern = Pattern.compile("(get|is)([A-Z].*)");

    private final AccessibleObject location;
    private final BiFunction<Object, Object, Object> wither;
    private final Class<?> holderType;
    private final Function<Object, Id> idProvider;
    private final Class<?> entityType;

    private final EntityHelper entityHelper;
    private final Serializer serializer;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Getter(lazy = true)
    private final ImmutableEntity<?> emptyEntity = ImmutableEntity.builder()
            .type((Class) entityType)
            .entityHelper(entityHelper)
            .serializer(serializer)
            .holder(this)
            .idProperty(idProvider.apply(entityType).property())
            .build();

    /**
     * Retrieves or creates a cached {@code AnnotatedEntityHolder} at the given member location.
     *
     * @param ownerType    the class that owns the annotated member
     * @param location     the accessible field or method annotated with @Member
     * @param entityHelper the helper used to apply/validate/apply logic to nested entities
     * @param serializer   the serializer used for cloning values during updates
     * @return a cached or new instance of {@code AnnotatedEntityHolder}
     */
    public static AnnotatedEntityHolder getEntityHolder(Class<?> ownerType, AccessibleObject location,
                                                        EntityHelper entityHelper, Serializer serializer) {
        return cache.computeIfAbsent(location,
                                     l -> new AnnotatedEntityHolder(ownerType, l, entityHelper, serializer));
    }

    private static final Function<Class<?>, Optional<MemberInvoker>> entityIdInvokerCache = memoize(
            entityType -> getAnnotatedProperty(
                    entityType, EntityId.class).map(a -> DefaultMemberInvoker.asInvoker((java.lang.reflect.Member) a)));

    private AnnotatedEntityHolder(Class<?> ownerType, AccessibleObject location,
                                  EntityHelper entityHelper, Serializer serializer) {
        this.entityHelper = entityHelper;
        this.serializer = serializer;
        this.location = location;
        this.holderType = ReflectionUtils.getPropertyType(location);
        this.entityType = getCollectionElementType(location).orElse(holderType);
        Member member = location.getAnnotation(Member.class);
        String pathToId = member.idProperty();
        this.idProvider = pathToId.isBlank() ?
                v -> (v == null ? Optional.<MemberInvoker>empty() : entityIdInvokerCache.apply(v.getClass())).map(
                                p -> new Id(p.invoke(v), p.getMember().getName()))
                        .orElseGet(() -> {
                            if (ifClass(v) instanceof Class<?> c) {
                                return new Id(null, getAnnotatedProperty(c, EntityId.class)
                                        .map(ReflectionUtils::getName).orElse(null));
                            }
                            return new Id(null, null);
                        }) :
                v -> new Id(readProperty(pathToId, v).orElse(null), pathToId);
        this.wither = computeWither(ownerType, location, serializer, member);
    }

    private static BiFunction<Object, Object, Object> computeWither(
            Class<?> ownerType, AccessibleObject location, Serializer serializer, Member member) {
        String propertyName = decapitalize(Optional.of(getName(location)).map(name -> Optional.of(
                        getterPattern.matcher(name)).map(matcher -> matcher.matches() ? matcher.group(2) : name)
                .orElse(name)).orElseThrow());
        Class<?>[] witherParams = new Class<?>[]{ReflectionUtils.getPropertyType(location)};
        Stream<Method> witherCandidates = ReflectionUtils.getAllMethods(ownerType).stream().filter(
                m -> m.getReturnType().isAssignableFrom(ownerType) || m.getReturnType().equals(void.class));
        witherCandidates = member.wither().isBlank() ?
                witherCandidates.filter(m -> Arrays.equals(witherParams, m.getParameterTypes())
                                             && m.getName().toLowerCase().contains(propertyName.toLowerCase())) :
                witherCandidates.filter(m -> Objects.equals(member.wither(), m.getName()));
        Optional<BiFunction<Object, Object, Object>> wither =
                witherCandidates.findFirst().map(m -> (o, h) -> call(() -> m.invoke(o, h)));
        return wither.orElseGet(() -> {
            AtomicBoolean warningIssued = new AtomicBoolean();
            MemberInvoker field = ReflectionUtils.getField(ownerType, propertyName)
                    .map(DefaultMemberInvoker::asInvoker).orElse(null);
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
                        field.invoke(o, h);
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

    /**
     * Returns the set of {@link ImmutableEntity} instances that are defined at the member location within the specified
     * parent entity. This includes actual members and an "empty" fallback entity used for creating new entities or
     * assertions to carry out before an entity is created.
     *
     * @param parent the parent entity instance
     * @return a stream of nested {@link ImmutableEntity} instances
     */
    public Stream<? extends ImmutableEntity<?>> getEntities(Entity<?> parent) {
        if (parent.get() == null) {
            return Stream.empty();
        }
        Object holderValue = getValue(location, parent.get(), false);
        Class<?> type = holderValue == null ? holderType : holderValue.getClass();
        ImmutableEntity<?> emptyEntity = getEmptyEntity().toBuilder().parent(parent).build();
        if (holderValue == null) {
            return Stream.of(emptyEntity);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return Stream.concat(
                    ((Collection<?>) holderValue).stream().map(v -> createEntity(v, idProvider, parent).orElse(null))
                            .filter(Objects::nonNull),
                    Stream.of(emptyEntity));
        } else if (Map.class.isAssignableFrom(type)) {
            return Stream.concat(
                    ((Map<?, ?>) holderValue).entrySet().stream().flatMap(e -> createEntity(
                            e.getValue(), v -> new Id(e.getKey(), idProvider.apply(v).property()), parent).stream()),
                    Stream.of(emptyEntity));
        } else {
            return createEntity(holderValue, idProvider, parent).stream();
        }
    }


    @SuppressWarnings({"unchecked"})
    private Optional<ImmutableEntity<?>> createEntity(Object member, Function<Object, Id> idProvider,
                                                      Entity<?> parent) {
        if (member == null) {
            return empty();
        }
        Id id = idProvider.apply(member);
        return Optional.of(ImmutableEntity.builder().id(id.value()).type((Class<Object>) member.getClass())
                                   .value(member).idProperty(id.property()).parent(parent).holder(this)
                                   .entityHelper(entityHelper).serializer(serializer).build());
    }


    /**
     * Updates the parent object with the new state of a child entity. Uses a wither method if available; otherwise
     * attempts a reflective update through cloning and direct field access. If the entity has been removed (value has
     * become {@code null}), the entity is removed from the owner object if possible.
     * <p>
     * Note that this method typically returns a new clone of the owner object containing the updated child entity
     * value.
     *
     * @param owner  the parent object containing the member field
     * @param before the old entity (before update)
     * @param after  the new entity (after update)
     * @return a new updated parent object, or the original if mutation fails
     */
    @SneakyThrows
    public Object updateOwner(Object owner, Entity<?> before, Entity<?> after) {
        Object holder = ReflectionUtils.getValue(location, owner);
        if (Collection.class.isAssignableFrom(holderType)) {
            Collection<Object> collection = serializer.clone(holder);
            if (collection == null) {
                collection = new ArrayList<>();
            }
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
            if (map == null) {
                map = new LinkedHashMap<>();
            }
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
