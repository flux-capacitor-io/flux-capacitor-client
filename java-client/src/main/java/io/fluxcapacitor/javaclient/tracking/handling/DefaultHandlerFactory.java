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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;

@RequiredArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {
    private final MessageType messageType;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(
            Object target, String consumer, HandlerConfiguration handlerConfiguration) {
        return Optional.ofNullable(getHandlerAnnotation(messageType))
                .filter(a -> hasHandlerMethods(target.getClass(), a, handlerConfiguration))
                .map(a -> handlerInterceptor.wrap(
                        HandlerInspector.createHandler(target, a, parameterResolvers, handlerConfiguration), consumer));
    }

    private static Class<? extends Annotation> getHandlerAnnotation(MessageType messageType) {
        switch (messageType) {
            case COMMAND:
                return HandleCommand.class;
            case EVENT:
                return HandleEvent.class;
            case NOTIFICATION:
                return HandleNotification.class;
            case QUERY:
                return HandleQuery.class;
            case RESULT:
                return HandleResult.class;
            case ERROR:
                return HandleError.class;
            case SCHEDULE:
                return HandleSchedule.class;
            case METRICS:
                return HandleMetrics.class;
            case WEBREQUEST:
                return HandleWeb.class;
            default:
                return null;
        }
    }
}
