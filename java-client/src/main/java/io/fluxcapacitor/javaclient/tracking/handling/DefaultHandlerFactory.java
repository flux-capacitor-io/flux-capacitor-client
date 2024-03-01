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
import io.fluxcapacitor.javaclient.modeling.ViewRepository;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.web.HandleWeb;
import io.fluxcapacitor.javaclient.web.HandleWebResponse;
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

@RequiredArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {

    private final MessageType messageType;
    private final HandlerDecorator defaultDecorator;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final MessageFilter<? super DeserializingMessage> messageFilter;
    private final Class<? extends Annotation> handlerAnnotation;

    private final Function<Class<?>, ViewRepository> viewRepositorySupplier;

    public DefaultHandlerFactory(MessageType messageType, HandlerDecorator defaultDecorator,
                                 List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
                                 Function<Class<?>, ViewRepository> viewRepositorySupplier) {
        this.messageType = messageType;
        this.defaultDecorator = defaultDecorator;
        this.parameterResolvers = parameterResolvers;
        this.viewRepositorySupplier = memoize(viewRepositorySupplier);
        this.handlerAnnotation = getHandlerAnnotation(messageType);
        this.messageFilter = defaultMessageFilter();
    }

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(Object target, HandlerFilter handlerFilter,
                                                                 List<HandlerInterceptor> extraInterceptors) {
        Class<?> targetClass = HandlerFactory.getTargetClass(target);
        HandlerDecorator handlerDecorator =
                Stream.concat(extraInterceptors.stream(), Stream.of(defaultDecorator))
                        .reduce(HandlerDecorator::andThen).orElseThrow();
        return Optional.of(handlerAnnotation)
                .map(a -> HandlerConfiguration.<DeserializingMessage>builder().methodAnnotation(a)
                        .handlerFilter(handlerFilter).messageFilter(messageFilter).build())
                .filter(config -> hasHandlerMethods(targetClass, config))
                .map(config -> buildHandler(target, config))
                .map(handlerDecorator::wrap);
    }

    private Handler<DeserializingMessage> buildHandler(Object target, HandlerConfiguration<DeserializingMessage> config) {
        if (target instanceof Class<?> targetClass) {
            {
                View view = ReflectionUtils.getTypeAnnotation(targetClass, View.class);
                if (view != null) {
                    return new ViewHandler(
                            targetClass, HandlerInspector.inspect(targetClass, parameterResolvers, config),
                            viewRepositorySupplier.apply(targetClass));
                }
            }

            {
                var trackSelf
                        = Optional.ofNullable(ReflectionUtils.getTypeAnnotation(targetClass, TrackSelf.class))
                        .or(() -> Optional.ofNullable(targetClass.getPackage())
                                .flatMap(p -> ReflectionUtils.getPackageAnnotation(p, TrackSelf.class)));
                if (trackSelf.isPresent()) {
                    MessageFilter<DeserializingMessage> selfFilter =
                            (message, method) -> targetClass.isAssignableFrom(message.getPayloadClass());
                    return HandlerInspector.createHandler(
                            DeserializingMessage::getPayload, targetClass, parameterResolvers,
                            config.toBuilder().messageFilter(selfFilter.and(config.messageFilter())).build());
                }
            }

            Supplier<Object> instanceSupplier = memoize(() -> ReflectionUtils.asInstance(targetClass));
            return HandlerInspector.createHandler(
                    m -> targetClass.equals(m.getPayloadClass()) ? m.getPayload() : instanceSupplier.get(),
                    targetClass, parameterResolvers, config);
        }
        return HandlerInspector.createHandler(target, parameterResolvers, config);
    }

    protected Class<? extends Annotation> getHandlerAnnotation(MessageType messageType) {
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

    @SuppressWarnings("unchecked")
    protected MessageFilter<? super DeserializingMessage> defaultMessageFilter() {
        var payloadFilter = new PayloadFilter();
        var result = parameterResolvers.stream().flatMap(r -> r instanceof MessageFilter<?>
                        ? Stream.of((MessageFilter<HasMessage>) r) : Stream.empty())
                .reduce(MessageFilter::and).map(f -> f.and(payloadFilter))
                .orElse(payloadFilter);
        return messageType == MessageType.WEBREQUEST ? WebRequest.getWebRequestFilter().and(result) : result;
    }

}
