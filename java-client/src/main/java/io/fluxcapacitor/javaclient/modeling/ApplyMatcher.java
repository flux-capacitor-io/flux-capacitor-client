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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerInvoker.DelegatingHandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class ApplyMatcher implements HandlerMatcher<Entity<?>, DeserializingMessage> {

    private final Function<Class<?>, HandlerMatcher<Object, DeserializingMessage>> invokers;

    public ApplyMatcher(List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.invokers = memoize(type -> inspect(type, parameterResolvers, Apply.class));
    }

    @Override
    public Optional<HandlerInvoker> findInvoker(Entity<?> entity, DeserializingMessage event) {
        var message = new DeserializingMessageWithEntity(event, entity);
        Class<?> entityType = entity.type();
        return invokers.apply(entityType).findInvoker(entity.get(), message)
                .or(() -> invokers.apply(message.getPayloadClass()).findInvoker(message.getPayload(), message)
                        .filter(i -> {
                            if (i.getMethod() instanceof Method) {
                                Class<?> returnType = ((Method) i.getMethod()).getReturnType();
                                return entityType.isAssignableFrom(returnType)
                                       || returnType.isAssignableFrom(entityType) || returnType.equals(void.class);
                            }
                            return false;
                        }))
                .map(i -> new DelegatingHandlerInvoker(i) {
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
}
