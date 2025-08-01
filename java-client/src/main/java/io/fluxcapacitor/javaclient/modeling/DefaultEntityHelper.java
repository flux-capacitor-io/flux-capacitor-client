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

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;
import io.fluxcapacitor.javaclient.tracking.handling.Invocation;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.Value;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.asStream;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValues;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;

/**
 * The {@code DefaultEntityHelper} provides the default implementation of the {@link EntityHelper} interface.
 * <p>
 * It is responsible for orchestrating the behavior of domain model entities with respect to: - Intercepting messages
 * using {@code @InterceptApply} handlers - Applying updates or events via {@code @Apply} handlers - Validating updates
 * and asserting legal state transitions using {@code @AssertLegal} handlers
 * <p>
 * This helper is heavily used during message handling and event sourcing to delegate message processing and validation
 * logic to methods annotated on the entity class or its nested members.
 * <p>
 * The class supports efficient handler resolution through memoized lookups, enabling fast, repeatable application of
 * complex domain behaviors.
 */
public class DefaultEntityHelper implements EntityHelper {

    /**
     * Default aggregate annotation used when no explicit @Aggregate is found.
     */
    private static final Aggregate defaultAggregateAnnotation = DefaultAggregate.class.getAnnotation(Aggregate.class);

    /**
     * Default aggregate annotation used when the entity type is unknown. This is used to avoid caching (empty) aggregates of type Object.
     */
    private static final Aggregate unknownAggregateAnnotation = UnknownAggregate.class.getAnnotation(Aggregate.class);

    /**
     * Caches resolved @Aggregate annotations for faster repeated access.
     */
    private static final Function<Class<?>, Aggregate> annotationCache = memoize(
            type -> Object.class.equals(type) ? unknownAggregateAnnotation : Optional.<Aggregate>ofNullable(
            ReflectionUtils.getTypeAnnotation(type, Aggregate.class)).orElse(defaultAggregateAnnotation));

    /**
     * Returns the cached or default @Aggregate annotation for a given type.
     */
    public static Aggregate getRootAnnotation(Class<?> type) {
        return annotationCache.apply(type);
    }

    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> interceptMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, DeserializingMessage>> applyMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> assertLegalMatchers;
    private final boolean disablePayloadValidation;

    /**
     * Creates a new helper using the given parameter resolvers and configuration.
     *
     * @param parameterResolvers       Resolvers used to inject values into annotated handler methods
     * @param disablePayloadValidation If true, disables bean validation of payloads
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultEntityHelper(List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
                               boolean disablePayloadValidation) {
        this.interceptMatchers = memoize(type -> inspect(type, (List) parameterResolvers, InterceptApply.class));
        this.applyMatchers = memoize(type -> inspect(type, parameterResolvers, Apply.class));
        this.assertLegalMatchers = memoize(type -> inspect(
                type, (List) parameterResolvers, HandlerConfiguration.builder().methodAnnotation(AssertLegal.class)
                        .invokeMultipleMethods(true).build()));
        this.disablePayloadValidation = disablePayloadValidation;
    }

    /**
     * Intercepts the given value using {@code @InterceptApply} methods defined on the entity. Interceptors may
     * transform or replace the original message.
     */
    @Override
    public Stream<?> intercept(Object value, Entity<?> entity) {
        Object payload = Optional.ofNullable(DeserializingMessage.getCurrent())
                .map(DeserializingMessage::getMetadata)
                .<Object>map(currentMetadata -> {
                    if (value instanceof HasMessage) {
                        Message result = Message.asMessage(value);
                        return result.withMetadata(currentMetadata.with(result.getMetadata()));
                    }
                    return new Message(value, currentMetadata);
                }).orElse(value);
        MessageWithEntity m = new MessageWithEntity(payload, entity);
        return getInterceptInvoker(m)
                .map(i -> asStream(i.invoke()).flatMap(v -> {
                    Message message = v instanceof HasMessage hm
                            ? hm.toMessage() : Message.asMessage(v).withTimestamp(m.getTimestamp());
                    message = message.withMetadata(m.getMetadata().with(message.getMetadata()));
                    if (message.getPayloadClass().equals(m.getPayloadClass())) {
                        return Stream.of(message);
                    }
                    return intercept(message, entity);
                })).orElseGet(() -> Stream.of(value));
    }

    /**
     * Recursively resolves the best-matching interceptor method, including nested members.
     */
    protected Optional<HandlerInvoker> getInterceptInvoker(MessageWithEntity m) {
        return interceptMatchers.apply(m.getPayloadClass()).getInvoker(m.getPayload(), m)
                .or(() -> {
                    for (Entity<?> child : m.getEntity().possibleTargets(m.getPayload())) {
                        var childInvoker = getInterceptInvoker(m.withEntity(child));
                        if (childInvoker.isPresent()) {
                            return childInvoker;
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * Finds a handler method annotated with {@code @Apply} and wraps it to preserve apply context flags.
     */
    @Override
    public Optional<HandlerInvoker> applyInvoker(DeserializingMessage event, Entity<?> entity, boolean searchChildren) {
        var message = new DeserializingMessageWithEntity(event, entity);
        Class<?> entityType = entity.type();
        Optional<HandlerInvoker> result = applyMatchers.apply(entityType).getInvoker(entity.get(), message)
                .or(() -> applyMatchers.apply(message.getPayloadClass()).getInvoker(message.getPayload(), message)
                        .filter(i -> {
                            if (i.getMethod() instanceof Method) {
                                Class<?> returnType = ((Method) i.getMethod()).getReturnType();
                                return entityType.isAssignableFrom(returnType)
                                       || returnType.isAssignableFrom(entityType) || returnType.equals(void.class);
                            }
                            return false;
                        }))
                .map(i -> new HandlerInvoker.DelegatingHandlerInvoker(i) {
                    @Override
                    public Object invoke(BiFunction<Object, Object, Object> combiner) {
                        return message.apply(m -> {
                            boolean wasApplying = Entity.isApplying();
                            try {
                                Entity.applying.set(true);
                                Object result = delegate.invoke();
                                if (result == null && !delegate.expectResult()) {
                                    return entity.get(); //Annotated method returned void - apparently the entity is mutable
                                }
                                return result;
                            } finally {
                                Entity.applying.set(wasApplying);
                            }
                        });
                    }
                });
        if (result.isEmpty() && searchChildren) {
            for (Entity<?> e : entity.possibleTargets(message.getPayload())) {
                result = applyInvoker(event, e, true);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return result;
    }

    /**
     * Performs a validation check using {@code @AssertLegal} handlers for the provided payload. This method is called
     * both before and after the handler completes, depending on the annotation's settings.
     */
    @Override
    public <E extends Exception> void assertLegal(Object value, Entity<?> entity) throws E {
        assertLegal(value, entity, false);
        Invocation.whenHandlerCompletes((r, e) -> {
            if (e == null) {
                assertLegal(value, entity, true);
            }
        });
    }

    private void assertLegal(Object value, Entity<?> entity, boolean afterHandler) {
        if (value == null) {
            return;
        }

        //value needs to be valid
        if (!disablePayloadValidation && !afterHandler) {
            ValidationUtils.assertValid(value instanceof HasMessage hasMessage ? hasMessage.getPayload() : value);
        }

        //check recursive on value and entity
        assertLegalRecursive(value, entity, afterHandler,
                             value instanceof HasMessage hasMessage ? hasMessage.getPayload() : value);
    }

    /**
     * Performs recursive validation for the value and any nested properties or child entities.
     */
    private void assertLegalRecursive(Object value, Entity<?> entity, boolean afterHandler, Object payload) {
        assertLegalValue(payload.getClass(), payload, value, entity, afterHandler);
        assertLegalValue(entity.type(), entity.get(), value, entity, afterHandler);
        entity.possibleTargets(payload).forEach(e -> assertLegalRecursive(value, e, afterHandler, payload));
    }

    /**
     * Resolves and invokes @AssertLegal handlers for the given target.
     */
    private void assertLegalValue(Class<?> targetType, Object target, Object value, Entity<?> entity,
                                  boolean afterHandler) {
        if (value == null) {
            return;
        }
        MessageWithEntity message = new MessageWithEntity(value, entity);
        Collection<Object> additionalProperties = new HashSet<>(getAnnotatedPropertyValues(target, AssertLegal.class));
        assertLegalMatchers.apply(targetType).getInvoker(target, message)
                .filter(i -> getAnnotation(i.getMethod(), AssertLegal.class).map(
                        a -> a.afterHandler() == afterHandler).orElse(false))
                .ifPresent(s -> {
                    Object additionalObject = s.invoke((first, second) -> Stream.concat(
                            first instanceof Collection<?> c ? c.stream() : Stream.of(first),
                            second instanceof Collection<?> c ? c.stream() : Stream.of(second)).toList());
                    if (additionalObject instanceof Collection<?>) {
                        additionalProperties.addAll((Collection<?>) additionalObject);
                    } else {
                        additionalProperties.add(additionalObject);
                    }
                });
        additionalProperties.stream().filter(Objects::nonNull)
                .forEach(p -> assertLegalValue(p.getClass(), p, value, entity, afterHandler));
    }

    /**
     * Returns an exception if the value is illegal for the current entity, or {@code Optional.empty()} if legal.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <E extends Exception> Optional<E> checkLegality(Object value, Entity<?> entity) {
        try {
            assertLegal(value, entity);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of((E) e);
        }
    }

    /**
     * Convenience method that returns {@code true} if {@link #checkLegality} yields no error.
     */
    @Override
    public boolean isLegal(Object value, Entity<?> entity) {
        return checkLegality(value, entity).isEmpty();
    }

    /**
     * Fallback default annotation for when an entity type lacks an explicit @Aggregate declaration.
     */
    @Aggregate
    static class DefaultAggregate {
    }

    /**
     * Annotation for an (empty) entity of type Object (type unknown).
     */
    @Aggregate(cached = false)
    static class UnknownAggregate {
    }

    /**
     * Wraps a message and its corresponding entity for use in interception or handler invocation.
     */
    @Value
    protected static class MessageWithEntity implements HasMessage, HasEntity {
        Entity<?> entity;
        Object payload;

        public MessageWithEntity(Object payload, Entity<?> entity) {
            this.payload = payload;
            this.entity = entity;
        }

        public MessageWithEntity withEntity(Entity<?> entity) {
            return new MessageWithEntity(payload, entity);
        }

        @Override
        public Metadata getMetadata() {
            return payload instanceof HasMetadata ? ((HasMetadata) payload).getMetadata() : Metadata.empty();
        }

        @Override
        public Message toMessage() {
            return Message.asMessage(payload);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> R getPayload() {
            return payload instanceof HasMessage ? ((HasMessage) payload).getPayload() : (R) payload;
        }
    }

    /**
     * Decorates a {@link DeserializingMessage} with an associated entity.
     */
    @Value
    protected static class DeserializingMessageWithEntity extends DeserializingMessage implements HasEntity {
        Entity<?> entity;

        public DeserializingMessageWithEntity(DeserializingMessage message, Entity<?> entity) {
            super(message);
            this.entity = entity;
        }
    }
}
