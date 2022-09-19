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
import io.fluxcapacitor.javaclient.modeling.EntityParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<? extends T> handlerType;
    private final HandlerInvoker<DeserializingMessage> entityInvoker;
    private final EntityParameterResolver entityResolver = new EntityParameterResolver();
    private final Function<Class<?>, HandlerInvoker<DeserializingMessage>> eventInvokers;

    public AnnotatedEventSourcingHandler(Class<? extends T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.entityInvoker = inspect(handlerType, parameterResolvers,
                                     HandlerConfiguration.builder().methodAnnotation(ApplyEvent.class).build());
        this.eventInvokers = memoize(eventType -> {
            List<ParameterResolver<? super DeserializingMessage>> paramResolvers = new ArrayList<>(parameterResolvers);
            paramResolvers.stream().filter(p -> p instanceof PayloadParameterResolver).findFirst().ifPresentOrElse(
                    payloadResolver -> paramResolvers.add(paramResolvers.indexOf(payloadResolver), entityResolver),
                    () -> paramResolvers.add(entityResolver));
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
    public T invoke(Entity<T> entity, DeserializingMessage message) {
        message = new DeserializingMessageWithEntity(message, entity);
        return message.apply(m -> {
            Object result;
            HandlerInvoker<DeserializingMessage> invoker;
            T model = entity.get();
            boolean handledByEntity;
            try {
                handledByEntity = entityInvoker.canHandle(model, m);
                invoker = handledByEntity ? entityInvoker : eventInvokers.apply(m.getPayloadClass());
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
        });
    }

    @Override
    public boolean canHandle(Entity<T> entity, DeserializingMessage message) {
        message = new DeserializingMessageWithEntity(message, entity);
        return entityInvoker.canHandle(entity.get(), message)
               || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
    }

}
