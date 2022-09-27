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
import io.fluxcapacitor.common.handling.HandlerInvoker.DelegatingHandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.DeserializingMessageWithEntity;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<? extends T> handlerType;
    private final HandlerMatcher<DeserializingMessage> entityInvoker;
    private final EntityParameterResolver entityResolver = new EntityParameterResolver();
    private final Function<Class<?>, HandlerMatcher<DeserializingMessage>> eventInvokers;

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
    public Optional<HandlerInvoker> findInvoker(Entity<T> entity, DeserializingMessage event) {
        var message = new DeserializingMessageWithEntity(event, entity);
        return entityInvoker.findInvoker(entity.get(), message)
                .or(() -> eventInvokers.apply(message.getPayloadClass()).findInvoker(message.getPayload(), message))
                .map(i -> new DelegatingHandlerInvoker(i) {
                    @Override
                    public Object invoke(BiFunction<Object, Object, Object> combiner) {
                        return message.apply(m -> {
                            Object entityValue = entity.get();
                            Object result = delegate.invoke();
                            if (entityValue == null) {
                                return handlerType.cast(result);
                            }
                            if (handlerType.isInstance(result)) {
                                return handlerType.cast(result);
                            }
                            if (result == null && delegate.expectResult()) {
                                return null; //this handler has deleted the entity on purpose
                            }
                            return entityValue; //Annotated method returned void - apparently the entity is mutable
                        });
                    }
                });
    }
}
