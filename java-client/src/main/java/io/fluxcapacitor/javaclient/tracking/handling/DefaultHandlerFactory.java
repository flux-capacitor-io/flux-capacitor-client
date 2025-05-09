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
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.handling.DefaultHandler;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.web.HandleWeb;
import io.fluxcapacitor.javaclient.web.HandleWebResponse;
import io.fluxcapacitor.javaclient.web.SocketEndpoint;
import io.fluxcapacitor.javaclient.web.SocketEndpointHandler;
import io.fluxcapacitor.javaclient.web.WebHandlerMatcher;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

@AllArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {

    private final MessageType messageType;
    private final HandlerDecorator defaultDecorator;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final MessageFilter<? super DeserializingMessage> messageFilter;
    private final Class<? extends Annotation> handlerAnnotation;
    private final Function<Class<?>, HandlerRepository> handlerRepositorySupplier;
    private final RepositoryProvider repositoryProvider;

    public DefaultHandlerFactory(MessageType messageType, HandlerDecorator defaultDecorator,
                                 List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
                                 Function<Class<?>, HandlerRepository> handlerRepositorySupplier,
                                 RepositoryProvider repositoryProvider) {
        this.messageType = messageType;
        this.defaultDecorator = defaultDecorator;
        this.parameterResolvers = parameterResolvers;
        this.handlerRepositorySupplier = handlerRepositorySupplier;
        this.repositoryProvider = repositoryProvider;
        this.handlerAnnotation = getHandlerAnnotation(messageType);
        this.messageFilter = computeMessageFilter();
    }

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(Object target, HandlerFilter handlerFilter,
                                                                 List<HandlerInterceptor> extraInterceptors) {
        Class<?> targetClass = HandlerFactory.getTargetClass(target);
        HandlerDecorator handlerDecorator =
                ObjectUtils.concat(extraInterceptors.stream(), Stream.of(defaultDecorator))
                        .reduce(HandlerDecorator::andThen).orElseThrow();
        return Optional.of(handlerAnnotation)
                .map(a -> HandlerConfiguration.<DeserializingMessage>builder().methodAnnotation(a)
                        .handlerFilter(handlerFilter).messageFilter(messageFilter).build())
                .filter(config -> hasHandlerMethods(targetClass, config))
                .map(config -> buildHandler(target, config))
                .map(handlerDecorator::wrap);
    }

    protected Handler<DeserializingMessage> buildHandler(@NonNull Object target,
                                                         HandlerConfiguration<DeserializingMessage> config) {

        if (ifClass(target) instanceof Class<?> targetClass) {
            {
                Stateful handler = ReflectionUtils.getTypeAnnotation(targetClass, Stateful.class);
                if (handler != null) {
                    return new StatefulHandler(targetClass, createHandlerMatcher(targetClass, config),
                                               handlerRepositorySupplier.apply(targetClass));
                }
            }

            {
                SocketEndpoint handler = ReflectionUtils.getTypeAnnotation(targetClass, SocketEndpoint.class);
                if (handler != null) {
                    return new SocketEndpointHandler(
                            targetClass, createHandlerMatcher(targetClass, config),
                            createHandlerMatcher(SocketEndpointHandler.SocketEndpointWrapper.class, config),
                            repositoryProvider);
                }
            }

            {
                var trackSelf
                        = Optional.ofNullable(ReflectionUtils.getTypeAnnotation(targetClass, TrackSelf.class))
                        .or(() -> Optional.ofNullable(targetClass.getPackage())
                                .flatMap(p -> ReflectionUtils.getPackageAnnotation(p, TrackSelf.class)));
                if (trackSelf.isPresent()) {
                    MessageFilter<DeserializingMessage> selfFilter =
                            (message, method, handlerAnnotation) -> targetClass.isAssignableFrom(
                                    message.getPayloadClass());
                    config = config.toBuilder().messageFilter(selfFilter.and(config.messageFilter())).build();
                    return createDefaultHandler(targetClass, DeserializingMessage::getPayload, config);
                }
            }

            Supplier<Object> instanceSupplier = memoize(() -> ReflectionUtils.asInstance(targetClass));
            return createDefaultHandler(targetClass, m -> targetClass.equals(m.getPayloadClass())
                    ? m.getPayload() : instanceSupplier.get(), config);
        }
        return createDefaultHandler(target.getClass(), m -> target, config);
    }

    protected Handler<DeserializingMessage> createDefaultHandler(
            Class<?> targetClass, Function<DeserializingMessage, ?> targetSupplier,
            HandlerConfiguration<DeserializingMessage> config) {
        return new DefaultHandler<>(targetClass, targetSupplier, createHandlerMatcher(targetClass, config));
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
            case DOCUMENT -> HandleDocument.class;
            case CUSTOM -> HandleCustom.class;
        };
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    protected HandlerMatcher<Object, DeserializingMessage> createHandlerMatcher(
            Class<?> targetClass, HandlerConfiguration<DeserializingMessage> config) {
        return switch (messageType) {
            case WEBREQUEST -> WebHandlerMatcher.create(targetClass, parameterResolvers, config);
            default -> HandlerInspector.inspect(targetClass, parameterResolvers, config);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected MessageFilter<? super DeserializingMessage> computeMessageFilter() {
        var defaultFilter = new PayloadFilter().and(new SegmentFilter());
        MessageFilter result = switch (messageType) {
            case CUSTOM -> defaultFilter.and((MessageFilter) new HandleCustomFilter());
            case DOCUMENT -> defaultFilter.and((MessageFilter) new HandleDocumentFilter());
            default -> defaultFilter;
        };
        return parameterResolvers.stream().flatMap(r -> r instanceof MessageFilter<?>
                        ? Stream.of((MessageFilter<HasMessage>) r) : Stream.empty())
                .reduce(MessageFilter::and).map(f -> f.and(result)).orElse(result);
    }

}
