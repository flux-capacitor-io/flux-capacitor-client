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

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@AllArgsConstructor
public class DefaultEventSourcingHandlerFactory implements EventSourcingHandlerFactory {

    @SuppressWarnings("Convert2MethodRef")
    private static final BiFunction<Class<?>, List<ParameterResolver<? super DeserializingMessage>>, EventSourcingHandler<?>>
            handlerCache = memoize((c, p) -> new AnnotatedEventSourcingHandler<>(c, p));

    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;

    @SuppressWarnings("unchecked")
    @Override
    public <T> EventSourcingHandler<T> forType(Class<?> type) {
        return (AnnotatedEventSourcingHandler<T>) handlerCache.apply(type, parameterResolvers);
    }
}
