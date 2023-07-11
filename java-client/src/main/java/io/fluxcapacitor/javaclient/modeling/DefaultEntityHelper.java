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
import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;
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

public class DefaultEntityHelper implements EntityHelper {

    private static final Aggregate defaultAggregateAnnotation = DefaultAggregate.class.getAnnotation(Aggregate.class);
    private static final Function<Class<?>, Aggregate> annotationCache = memoize(type -> Optional.ofNullable(
            ReflectionUtils.getTypeAnnotation(type, Aggregate.class)).orElse(defaultAggregateAnnotation));
    public static Aggregate getRootAnnotation(Class<?> type) {
        return annotationCache.apply(type);
    }

    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> interceptMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, DeserializingMessage>> applyMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> assertLegalMatchers;
    private final boolean disablePayloadValidation;

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

    @Override
    public Stream<?> intercept(Object value, Entity<?> entity) {
        MessageWithEntity m = new MessageWithEntity(value, entity);
        return getInterceptInvoker(m)
                .map(i -> asStream(i.invoke()).flatMap(v -> {
                    Message message = Message.asMessage(v);
                    message = message.withMetadata(m.getMetadata().with(message.getMetadata()));
                    if (message.getPayloadClass().equals(m.getPayloadClass())) {
                        return Stream.of(message);
                    }
                    return intercept(message, entity);
                })).orElseGet(() -> Stream.of(value));
    }

    protected Optional<HandlerInvoker> getInterceptInvoker(MessageWithEntity m) {
        return interceptMatchers.apply(m.getPayloadClass()).findInvoker(m.getPayload(), m)
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

    @Override
    public Optional<HandlerInvoker> applyInvoker(DeserializingMessage event, Entity<?> entity) {
        var message = new DeserializingMessageWithEntity(event, entity);
        Class<?> entityType = entity.type();
        return applyMatchers.apply(entityType).findInvoker(entity.get(), message)
                .or(() -> applyMatchers.apply(message.getPayloadClass()).findInvoker(message.getPayload(), message)
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
    }

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

        //check on value
        Object payload = value instanceof HasMessage hasMessage ? hasMessage.getPayload() : value;
        assertLegalValue(payload.getClass(), payload, value, entity, afterHandler);
        entity.possibleTargets(payload).forEach(
                e -> assertLegalValue(payload.getClass(), payload, value, e, afterHandler));

        //check on entity
        assertLegalValue(entity.type(), entity.get(), value, entity, afterHandler);
        entity.possibleTargets(payload).forEach(e -> assertLegalValue(e.type(), e.get(), value, e, afterHandler));
    }

    private void assertLegalValue(Class<?> targetType, Object target, Object value, Entity<?> entity,
                                  boolean afterHandler) {
        if (value == null) {
            return;
        }
        MessageWithEntity message = new MessageWithEntity(value, entity);
        Collection<Object> additionalProperties = new HashSet<>(getAnnotatedPropertyValues(target, AssertLegal.class));
        assertLegalMatchers.apply(targetType).findInvoker(target, message)
                .filter(i -> getAnnotation(i.getMethod(), AssertLegal.class).map(
                        a -> a.afterHandler() == afterHandler).orElse(false))
                .ifPresent(s -> {
                    Object additionalObject = s.invoke();
                    if (additionalObject instanceof Collection<?>) {
                        additionalProperties.addAll((Collection<?>) additionalObject);
                    } else {
                        additionalProperties.add(additionalObject);
                    }
                });
        additionalProperties.stream().filter(Objects::nonNull)
                .forEach(p -> assertLegalValue(p.getClass(), p, value, entity, afterHandler));
    }

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

    @Override
    public boolean isLegal(Object value, Entity<?> entity) {
        return checkLegality(value, entity).isEmpty();
    }

    @Aggregate
    static class DefaultAggregate {
    }

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

    @Value
    protected static class DeserializingMessageWithEntity extends DeserializingMessage implements HasEntity {
        Entity<?> entity;

        public DeserializingMessageWithEntity(DeserializingMessage message, Entity<?> entity) {
            super(message);
            this.entity = entity;
        }
    }
}
