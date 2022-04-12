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
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<? extends T> handlerType;
    private final HandlerInvoker<DeserializingMessage> aggregateInvoker;
    private final EventSourcingAggregateParameterResolver<T>
            aggregateResolver = new EventSourcingAggregateParameterResolver<>();
    private final Function<Class<?>, HandlerInvoker<DeserializingMessage>> eventInvokers;

    public AnnotatedEventSourcingHandler(Class<? extends T> handlerType) {
        this(handlerType, DeserializingMessage.defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<? extends T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.aggregateInvoker = inspect(handlerType, parameterResolvers,
                                        HandlerConfiguration.builder().methodAnnotation(ApplyEvent.class).build());
        this.eventInvokers = memoize(eventType -> {
            List<ParameterResolver<? super DeserializingMessage>> paramResolvers = new ArrayList<>(parameterResolvers);
            paramResolvers.add(0, aggregateResolver);
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
            boolean handledByAggregate;
            try {
                try {
                    aggregateResolver.setAggregate(entity);
                    handledByAggregate = aggregateInvoker.canHandle(model, m);
                    invoker = handledByAggregate ? aggregateInvoker : eventInvokers.apply(message.getPayloadClass());
                    result = invoker.invoke(handledByAggregate ? model : m.getPayload(), m);
                } catch (HandlerNotFoundException e) {
                    return model;
                }
                if (model == null) {
                    return handlerType.cast(result);
                }
                if (handlerType.isInstance(result)) {
                    return handlerType.cast(result);
                }
                if (result == null && invoker.expectResult(handledByAggregate ? model : m.getPayload(), m)) {
                    return null; //this handler has deleted the model on purpose
                }
                return model; //Annotated method returned void - apparently the model is mutable
            } finally {
                aggregateResolver.removeAggregate();
            }
        });
    }

    @Override
    public boolean canHandle(Entity<?, T> entity, DeserializingMessage message) {
        try {
            aggregateResolver.setAggregate(entity);
            return aggregateInvoker.canHandle(entity.get(), message)
                   || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
        } finally {
            aggregateResolver.removeAggregate();
        }
    }

    public static class EventSourcingAggregateParameterResolver<T> implements ParameterResolver<Object> {
        private final ThreadLocal<Entity<?, T>> currentAggregate = new ThreadLocal<>();

        @Override
        public Function<Object, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
            Class<T> aggregateType = currentAggregate.get().type();
            return parameter.getType().isAssignableFrom(aggregateType)
                   || aggregateType.isAssignableFrom(parameter.getType()) ? m -> currentAggregate.get().get() : null;
        }

        @Override
        public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value) {
            return currentAggregate.get() != null
                   && (currentAggregate.get().get() != null || ReflectionUtils.isNullable(parameter))
                   && ParameterResolver.super.matches(parameter, methodAnnotation, value);
        }

        @Override
        public boolean determinesSpecificity() {
            return true;
        }

        public void setAggregate(Entity<?, T> aggregate) {
            currentAggregate.set(aggregate);
        }

        public void removeAggregate() {
            currentAggregate.remove();
        }
    }
}
