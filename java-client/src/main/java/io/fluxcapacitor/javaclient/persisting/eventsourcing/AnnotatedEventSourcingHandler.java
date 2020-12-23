/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getProperty;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final ThreadLocal<T> currentAggregate = new ThreadLocal<>();
    private final Class<T> handlerType;
    private final HandlerInvoker<DeserializingMessage> aggregateInvoker;
    private final Function<Class<?>, HandlerInvoker<DeserializingMessage>> eventInvokers;

    public AnnotatedEventSourcingHandler(Class<T> handlerType) {
        this(handlerType, DeserializingMessage.defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.aggregateInvoker =
                inspect(handlerType, ApplyEvent.class, parameterResolvers, defaultHandlerConfiguration());
        this.eventInvokers = memoize(eventType -> {
            List<ParameterResolver<? super DeserializingMessage>> paramResolvers = new ArrayList<>(parameterResolvers);
            paramResolvers.add(0,
                               p -> p.getType().isAssignableFrom(handlerType) ? event -> currentAggregate.get() : null);
            return inspect(eventType, Apply.class, paramResolvers, defaultHandlerConfiguration());
        });
    }

    @Override
    public T invoke(T target, DeserializingMessage message) {
        return message.apply(m -> {
            Object result;
            HandlerInvoker<DeserializingMessage> invoker;
            try {
                currentAggregate.set(target);
                boolean handledByAggregate = aggregateInvoker.canHandle(target, m);
                invoker = handledByAggregate ? aggregateInvoker : eventInvokers.apply(message.getPayloadClass());
                result = invoker.invoke(handledByAggregate ? target : m.getPayload(), m);
            } catch (HandlerNotFoundException e) {
                if (target == null) {
                    throw e;
                }
                return target;
            } finally {
                currentAggregate.remove();
            }
            if (target == null) {
                return handlerType.cast(result);
            }
            if (handlerType.isInstance(result)) {
                return handlerType.cast(result);
            }
            if (result == null && invoker.expectResult(target, m)) {
                return null; //this handler has deleted the model on purpose
            }
            return target; //Annotated method returned void - apparently the model is mutable
        });
    }

    @Override
    public boolean canHandle(T target, DeserializingMessage message) {
        try {
            currentAggregate.set(target);
            return aggregateInvoker.canHandle(target, message)
                    || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
        } finally {
            currentAggregate.remove();
        }
    }


    @Value
    @Builder(toBuilder = true)
    public static class Entity {
        private static final Function<Class<?>, List<ChildCollection>> inspectionCache =
                memoize(ChildCollection::inspectChildren);

        Class<?> type;
        Object value;

        io.fluxcapacitor.javaclient.modeling.Entity annotation;
        @Getter(lazy = true)
        Object id = computeId(value);
        Entity parent;

        @Builder.Default
        Function<Object, Object> updater = Function.identity();

        @Getter(lazy = true)
        List<Entity> children = inspectionCache.apply(type).stream()
                .flatMap(c -> c.computeChildren(this)).collect(Collectors.toList());

        public static Entity forRoot(Object root, @NonNull Class<?> type) {
            return Entity.builder().value(root).type(root == null ? type : root.getClass()).updater(Function.identity())
                    .build();
        }

        public Object withEntity(Object value) {
            if (value == null) {
                return this.value;
            }
            Object id = computeId(value);
            Class<?> type = value.getClass();
            Entity match = getAncestralTree().filter(e -> e.matches(type, id)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException(
                            String.format("Failed to match value %s to an entity in %s", value, this.value)));
            Object result = match.updater.apply(value);
            if (result == null) {
                return this.value;
            }
            return Optional.ofNullable(match.getParent()).map(p -> p.withEntity(result)).orElse(result);
        }

        public Optional<Entity> findTarget(Parameter parameter, Object payload) {
            return getAncestralTree().filter(e -> e.matches(parameter, payload)).findFirst();
        }

        protected boolean matches(Class<?> type, Object id) {
            return type.isAssignableFrom(type) && Optional.ofNullable(getId()).map(i -> i.equals(id)).orElse(true);
        }

        protected boolean matches(Parameter parameter, Object payload) {
            if (!parameter.getType().isAssignableFrom(type)) {
                return false;
            }
            Object id = getId();
            if (id == null) {
                return true;
            }
            Object payloadId = computeId(payload);
            return (payloadId == null && annotation == null) || id.equals(payloadId);
        }

        protected Stream<Entity> getAncestralTree() {
            return Stream.concat(Stream.of(this), getChildren().stream().flatMap(Entity::getAncestralTree));
        }

        protected Object computeId(Object entityOrPayload) {
            return entityOrPayload == null ? null : Optional.ofNullable(annotation)
                    .map(a -> getProperty(a.entityId(), entityOrPayload))
                    .orElseGet(() -> getAnnotatedPropertyValue(entityOrPayload, EntityId.class));
        }
    }

    @Value
    @Builder
    protected static class ChildCollection {
        io.fluxcapacitor.javaclient.modeling.Entity annotation;
        Class<?> containerType, type;
        Function<Object, Object> getter, idGetter;
        BiFunction<Object, Object, Object> setter;

        protected Stream<Entity> computeChildren(Entity parentEntity) {
            Object parent = parentEntity.getValue();
            if (parent == null) {
                return Stream.empty();
            }

            Object container = getContainer(parent);

            if (container instanceof Collection) {
                Collection<?> collection = (Collection<?>) container;
                Function<Object, Object> updater = newValue -> setter.apply(
                        parent, newCollectionWithEntity(collection, newValue));
                Entity newEntity = Entity.builder().type(type).parent(parentEntity).annotation(annotation)
                        .updater(updater).build();
                return Stream.concat(
                        collection.stream().map(o -> newEntity.toBuilder().value(o).type(o.getClass()).build()),
                        Stream.of(newEntity));
            } else if (container instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) container;
                Function<Object, Object> updater =
                        newValue -> setter.apply(parent, newMapWithEntity(map, newValue));
                Entity newEntity = Entity.builder().type(type).parent(parentEntity).annotation(annotation)
                        .updater(updater).build();
                return Stream.concat(
                        map.values().stream().map(o -> newEntity.toBuilder().value(o).type(o.getClass()).build()),
                        Stream.of(newEntity));
            } else {
                Entity newEntity = Entity.builder().type(type).parent(parentEntity)
                        .updater(newValue -> setter.apply(parent, newValue))
                        .annotation(annotation)
                        .build();
                return container == null ? Stream.of(newEntity) : Stream.concat(
                        Stream.of(newEntity.toBuilder().value(container).type(container.getClass()).build()),
                        Stream.of(newEntity));
            }
        }

        private Object getContainer(Object parent) {
            Object container = getter.apply(parent);
            if (container != null
                    || (!Collection.class.isAssignableFrom(containerType) && !Map.class
                    .isAssignableFrom(containerType))) {
                return container;
            }
            try {
                Constructor<?> constructor = containerType.getDeclaredConstructor();
                return constructor.newInstance();
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate container instance of type " + containerType, e);
            }
            if (SortedSet.class.isAssignableFrom(containerType)) {
                return new TreeSet<>();
            }
            if (Set.class.isAssignableFrom(containerType)) {
                return new LinkedHashSet<>();
            }
            if (SortedMap.class.isAssignableFrom(containerType)) {
                return new TreeMap<>();
            }
            if (Map.class.isAssignableFrom(containerType)) {
                return new LinkedHashMap<>();
            }
            return new ArrayList<>();
        }

        @SneakyThrows
        private Collection<?> newCollectionWithEntity(Collection<?> existing, Object updatedEntity) {
            Object entityId = idGetter.apply(updatedEntity);
            if (entityId == null) {
                throw new IllegalArgumentException("Failed to get entity id from: " + updatedEntity);
            }
            List<Object> updatedCollection = new ArrayList<>(existing);
            updatedCollection.replaceAll(v -> entityId.equals(idGetter.apply(v)) ? updatedEntity : v);
            return existing.getClass().getDeclaredConstructor(Collection.class).newInstance(updatedCollection);
        }

        @SneakyThrows
        private Map<?, ?> newMapWithEntity(Map<?, ?> existing, Object updatedEntity) {
            Object entityId = idGetter.apply(updatedEntity);
            if (entityId == null) {
                throw new IllegalArgumentException("Failed to get entity id from: " + updatedEntity);
            }
            Map<Object, Object> result = new LinkedHashMap<>(existing);
            result.put(entityId, updatedEntity);
            return result;
        }

        private static List<ChildCollection> inspectChildren(Class<?> type) {
            return getAnnotatedProperties(type, io.fluxcapacitor.javaclient.modeling.Entity.class).stream()
                    .map(property -> {
                        io.fluxcapacitor.javaclient.modeling.Entity annotation =
                                property.getAnnotation(io.fluxcapacitor.javaclient.modeling.Entity.class);
                        Method setterMethod = getSetterMethod(type, property);

                        Class<?> entityType = getEntityType(type, property);
                        Function<Object, Object> idGetter = entityOrPayload -> Optional
                                .ofNullable(getProperty(annotation.entityId(), entityOrPayload)).orElseGet(
                                        () -> getAnnotatedPropertyValue(entityOrPayload, EntityId.class));
                        return ChildCollection.builder()
                                .annotation(annotation)
                                .containerType(getContainerType(type, property))
                                .type(entityType)
                                .getter(parent -> getProperty(property, parent))
                                .idGetter(idGetter)
                                .setter((parent, updatedChild) -> {
                                    try {
                                        Object result = setterMethod.invoke(parent, updatedChild);
                                        return result == null ? parent : result;
                                    } catch (Exception e) {
                                        throw new IllegalStateException(format(
                                                "Failed to invoke update method. Property property %s on parent %s",
                                                property, parent.getClass()));
                                    }
                                })
                                .build();
                    }).collect(Collectors.toList());

        }

        private static Class<?> getContainerType(Class<?> parentType, AccessibleObject property) {
            return property instanceof Field
                    ? ((Field) property).getType() : ((Method) property).getReturnType();
        }

        private static Class<?> getEntityType(Class<?> parentType, AccessibleObject property) {
            Type collectionType = property instanceof Field
                    ? ((Field) property).getGenericType() : ((Method) property).getGenericReturnType();
            return ReflectionUtils.getCollectionElementType(collectionType);
        }

        private static Method getSetterMethod(Class<?> parentType, AccessibleObject property) {
            io.fluxcapacitor.javaclient.modeling.Entity annotation =
                    property.getAnnotation(io.fluxcapacitor.javaclient.modeling.Entity.class);
            Type collectionType = property instanceof Field
                    ? ((Field) property).getGenericType() : ((Method) property).getGenericReturnType();
            Predicate<Method> setterTest = m -> {
                Class<?> returnType = m.getReturnType();
                if (!returnType.isAssignableFrom(parentType) && !returnType.equals(void.class)) {
                    return false;
                }
                if (m.getParameterCount() != 1) {
                    return false;
                }
                if (!m.getGenericParameterTypes()[0].equals(collectionType)) {
                    return false;
                }
                if (!isBlank(annotation.updateMethod())) {
                    return m.getName().equals(annotation.updateMethod());
                }
                return true;
            };
            return ReflectionUtils.getAllMethods(parentType).stream().filter(setterTest).map(
                    ReflectionUtils::ensureAccessible).findFirst().orElseThrow(
                    () -> new IllegalArgumentException(
                            format("No update method found for property %s on parent %s", property, parentType)));
        }

    }

}
