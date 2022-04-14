/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.isNullable;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<? extends T> handlerType;
    private final HandlerInvoker<DeserializingMessage> entityInvoker;
    private final EventSourcingEntityParameterResolver entityResolver = new EventSourcingEntityParameterResolver();
    private final Function<Class<?>, HandlerInvoker<DeserializingMessage>> eventInvokers;

    public AnnotatedEventSourcingHandler(Class<? extends T> handlerType) {
        this(handlerType, DeserializingMessage.defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<? extends T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.entityInvoker = inspect(handlerType, parameterResolvers,
                                     HandlerConfiguration.builder().methodAnnotation(ApplyEvent.class).build());
        this.eventInvokers = memoize(eventType -> {
            List<ParameterResolver<? super DeserializingMessage>> paramResolvers = new ArrayList<>(parameterResolvers);
            paramResolvers.add(0, entityResolver);
            return inspect(eventType, paramResolvers,
                           HandlerConfiguration.builder().methodAnnotation(Apply.class)
                                   .handlerFilter((type, executable) -> {
                                       if (executable instanceof Method) {
                                           Class<?> returnType = ((Method) executable).getReturnType();
                                           return handlerType.isAssignableFrom(returnType)
                                                  || returnType.isAssignableFrom(
                                                   handlerType) || returnType.equals(void.class);
                                       }
                                       return false;
                                   }).build());
        });
    }

    @Override
    public T invoke(Entity<?, T> entity, DeserializingMessage message) {
        return message.apply(m -> {
            Object result;
            HandlerInvoker<DeserializingMessage> invoker;
            T model = entity.get();
            boolean handledByEntity;
            try {
                try {
                    entityResolver.setEntity(entity);
                    handledByEntity = entityInvoker.canHandle(model, m);
                    invoker = handledByEntity ? entityInvoker : eventInvokers.apply(message.getPayloadClass());
                    result = invoker.invoke(handledByEntity ? model : m.getPayload(), m);
                } catch (HandlerNotFoundException e) {
                    return model;
                }
                if (model == null) {
                    return handlerType.cast(result);
                }
                if (handlerType.isInstance(result)) {
                    return handlerType.cast(result);
                }
                if (result == null && invoker.expectResult(handledByEntity ? model : m.getPayload(), m)) {
                    return null; //this handler has deleted the model on purpose
                }
                return model; //Annotated method returned void - apparently the model is mutable
            } finally {
                entityResolver.removeEntity();
            }
        });
    }

    @Override
    public boolean canHandle(Entity<?, T> entity, DeserializingMessage message) {
        try {
            entityResolver.setEntity(entity);
            return entityInvoker.canHandle(entity.get(), message)
                   || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
        } finally {
            entityResolver.removeEntity();
        }
    }

    protected static class EventSourcingEntityParameterResolver implements ParameterResolver<Object> {
        private final ThreadLocal<Entity<?, ?>> currentEntity = new ThreadLocal<>();

        @Override
        public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value) {
            return matches(parameter, currentEntity.get())
                   && ParameterResolver.super.matches(parameter, methodAnnotation, value);
        }

        protected boolean matches(Parameter parameter, Entity<?, ?> entity) {
            if (entity == null) {
                return false;
            }
            Class<?> entityType = entity.type();
            Class<?> parameterType = parameter.getType();
            if (entity.get() == null) {
                if (isNullable(parameter)
                    && (parameterType.isAssignableFrom(entityType) || entityType.isAssignableFrom(parameterType))) {
                    return true;
                }
            } else if (parameterType.isAssignableFrom(entityType)) {
                return true;
            }
            return matches(parameter, entity.parent());
        }

        @Override
        public Function<Object, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
            Supplier<?> supplier = resolve(parameter, currentEntity.get());
            return supplier == null ? null : m -> resolve(parameter, currentEntity.get()).get();
        }

        protected Supplier<?> resolve(Parameter parameter, Entity<?, ?> entity) {
            if (entity == null) {
                return null;
            }
            Class<?> type = entity.type();
            if (parameter.getType().isAssignableFrom(type) || type.isAssignableFrom(parameter.getType())) {
                return entity::get;
            }
            return resolve(parameter, entity.parent());
        }

        @Override
        public boolean determinesSpecificity() {
            return true;
        }

        public void setEntity(Entity<?, ?> entity) {
            currentEntity.set(entity);
        }

        public void removeEntity() {
            currentEntity.remove();
        }
    }
}
