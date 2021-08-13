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

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<T> handlerType;
    private final HandlerInvoker<DeserializingMessage> aggregateInvoker;
    private final LocalAggregateParameterResolver<T> aggregateResolver = new LocalAggregateParameterResolver<>();
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
            paramResolvers.add(0, aggregateResolver);
            return inspect(eventType, Apply.class, paramResolvers,
                           defaultHandlerConfiguration().toBuilder().handlerFilter((type, executable) -> {
                               if (executable instanceof Method) {
                                   Class<?> returnType = ((Method) executable).getReturnType();
                                   return handlerType.isAssignableFrom(returnType) || returnType.isAssignableFrom(
                                           handlerType) || returnType.equals(void.class);
                               }
                               return false;
                           }).build());
        });
    }

    @Override
    public T invoke(AggregateRoot<T> aggregate, DeserializingMessage message) {
        return message.apply(m -> {
            Object result;
            HandlerInvoker<DeserializingMessage> invoker;
            T model = aggregate.get();
            try {
                aggregateResolver.setAggregate(aggregate);
                boolean handledByAggregate = aggregateInvoker.canHandle(model, m);
                invoker = handledByAggregate ? aggregateInvoker : eventInvokers.apply(message.getPayloadClass());
                result = invoker.invoke(handledByAggregate ? model : m.getPayload(), m);
            } catch (HandlerNotFoundException e) {
                if (model == null) {
                    throw new HandlerNotFoundException(String.format(
                            "Aggregate '%2$s' of type %1$s does not exist and no applicable method exists in %1$s or %3$s that would instantiate a new %1$s.",
                            aggregate.type().getSimpleName(), aggregate.id(), message.getPayloadClass().getSimpleName()));
                }
                return model;
            } finally {
                aggregateResolver.removeAggregate();
            }
            if (model == null) {
                return handlerType.cast(result);
            }
            if (handlerType.isInstance(result)) {
                return handlerType.cast(result);
            }
            if (result == null && invoker.expectResult(model, m)) {
                return null; //this handler has deleted the model on purpose
            }
            return model; //Annotated method returned void - apparently the model is mutable
        });
    }

    @Override
    public boolean canHandle(AggregateRoot<T> aggregate, DeserializingMessage message) {
        try {
            aggregateResolver.setAggregate(aggregate);
            return aggregateInvoker.canHandle(aggregate, message)
                    || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
        } finally {
            aggregateResolver.removeAggregate();
        }
    }

    public static class LocalAggregateParameterResolver<T> implements ParameterResolver<Object> {
        private final ThreadLocal<AggregateRoot<T>> currentAggregate = new ThreadLocal<>();

        @Override
        public Function<Object, Object> resolve(Parameter parameter) {
            if (currentAggregate.get() == null) {
                return null;
            }
            Class<T> aggregateType = currentAggregate.get().type();
            return parameter.getType().isAssignableFrom(aggregateType)
                    || aggregateType.isAssignableFrom(parameter.getType()) ? m -> currentAggregate.get().get() : null;
        }

        public void setAggregate(AggregateRoot<T> aggregate) {
            currentAggregate.set(aggregate);
        }

        public void removeAggregate() {
            currentAggregate.remove();
        }
    }
}
