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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.web.HandleWeb;
import io.fluxcapacitor.javaclient.web.HandleWebResponse;
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;

@RequiredArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {
    public static Class<? extends Annotation> getHandlerAnnotation(MessageType messageType) {
        return switch (messageType) {
            case COMMAND -> HandleCommand.class;
            case EVENT -> HandleEvent.class;
            case NOTIFICATION -> HandleNotification.class;
            case QUERY -> HandleQuery.class;
            case RESULT -> HandleResult.class;
            case ERROR -> HandleError.class;
            case SCHEDULE -> HandleSchedule.class;
            case METRICS -> HandleMetrics.class;
            case WEBREQUEST -> HandleWeb.class;
            case WEBRESPONSE -> HandleWebResponse.class;
        };
    }

    private final MessageType messageType;
    private final HandlerDecorator defaultDecorator;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final MessageFilter<? super DeserializingMessage> messageFilter;

    public DefaultHandlerFactory(MessageType messageType, HandlerDecorator defaultDecorator,
                                 List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.messageType = messageType;
        this.defaultDecorator = defaultDecorator;
        this.parameterResolvers = parameterResolvers;
        this.messageFilter = defaultMessageFilter();
    }

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(Object target, HandlerFilter handlerFilter,
                                                                 List<HandlerInterceptor> extraInterceptors) {
        Class<?> targetClass = HandlerFactory.getTargetClass(target);
        Function<DeserializingMessage, Object> targetSupplier = getTargetSupplier(target);
        return createHandler(targetSupplier, targetClass, getHandlerAnnotation(messageType), handlerFilter,
                             getMessageFilter(targetSupplier), extraInterceptors);
    }

    protected Optional<Handler<DeserializingMessage>> createHandler(
            Function<DeserializingMessage, ?> targetSupplier,
            Class<?> targetClass, Class<? extends Annotation> handlerAnnotation,
            HandlerFilter handlerFilter, MessageFilter<? super DeserializingMessage> messageFilter,
            List<HandlerInterceptor> extraInterceptors) {
        HandlerDecorator handlerDecorator =
                Stream.concat(extraInterceptors.stream(), Stream.of(defaultDecorator))
                        .reduce(HandlerDecorator::andThen).orElseThrow();
        return Optional.ofNullable(handlerAnnotation)
                .map(a -> HandlerConfiguration.<DeserializingMessage>builder().methodAnnotation(a)
                        .handlerFilter(handlerFilter).messageFilter(messageFilter).build())
                .filter(config -> hasHandlerMethods(targetClass, config))
                .map(config -> HandlerInspector.createHandler(targetSupplier, targetClass, parameterResolvers, config))
                .map(handlerDecorator::wrap);
    }

    protected Function<DeserializingMessage, Object> getTargetSupplier(Object target) {
        if (target instanceof DynamicHandler h) {
            return h;
        }
        if (target instanceof Class<?> targetClass) {
            {
                var selfHandler = SelfHandler.asSelfHandler(targetClass, false);
                if (selfHandler.isPresent()) {
                    return selfHandler.get();
                }
            }
            var instance = ReflectionUtils.asInstance(targetClass);
            return m -> instance;
        }
        return m -> target;
    }


    protected MessageFilter<? super DeserializingMessage> getMessageFilter(
            Function<DeserializingMessage, Object> targetSupplier) {
        if (targetSupplier instanceof SelfHandler handler) {
            MessageFilter<DeserializingMessage> selfFilter =
                    (message, method) -> handler.getType().isAssignableFrom(message.getPayloadClass());
            return selfFilter.and(messageFilter);
        }
        return messageFilter;
    }

    @SuppressWarnings("unchecked")
    protected MessageFilter<? super DeserializingMessage> defaultMessageFilter() {
        var result = parameterResolvers.stream().flatMap(r -> r instanceof MessageFilter<?>
                        ? Stream.of((MessageFilter<HasMessage>) r) : Stream.empty())
                .reduce(MessageFilter::and).orElseGet(() -> (m, a) -> true);
        return messageType == MessageType.WEBREQUEST ? WebRequest.getWebRequestFilter().and(result) : result;
    }

}
