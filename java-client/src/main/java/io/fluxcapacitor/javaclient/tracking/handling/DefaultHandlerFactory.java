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
import io.fluxcapacitor.javaclient.web.HandleWeb;
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;

@RequiredArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {
    private static final Map<MessageType, BiPredicate<DeserializingMessage, Annotation>> messageFilterCache =
            new ConcurrentHashMap<>();
    private final MessageType messageType;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(Object target, String consumer,
                                                                 BiPredicate<Class<?>, Executable> handlerFilter) {
        return Optional.ofNullable(getHandlerAnnotation(messageType))
                .map(a -> HandlerConfiguration.<DeserializingMessage>builder()
                        .methodAnnotation(a).handlerFilter(handlerFilter)
                        .messageFilter(getMessageFilter(messageType))
                        .build())
                .filter(config -> hasHandlerMethods(target.getClass(), config))
                .map(config -> handlerInterceptor.wrap(
                        HandlerInspector.createHandler(target, parameterResolvers, config), consumer));
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

    private static BiPredicate<DeserializingMessage, Annotation> getMessageFilter(MessageType messageType) {
        return messageFilterCache.computeIfAbsent(messageType, t -> {
            if (t == MessageType.WEBREQUEST) {
                return WebRequest.getWebRequestFilter();
            }
            return (m, a) -> true;
        });
    }
}
