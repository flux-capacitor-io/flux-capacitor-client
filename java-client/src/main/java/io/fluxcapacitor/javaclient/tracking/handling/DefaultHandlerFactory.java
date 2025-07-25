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
import io.fluxcapacitor.javaclient.web.StaticFileHandler;
import io.fluxcapacitor.javaclient.web.WebHandlerMatcher;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.asClass;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

/**
 * Default implementation of the {@link HandlerFactory} for creating message handlers based on reflection.
 * <p>
 * This factory supports a wide range of handler types including:
 * <ul>
 *     <li>Simple class-based handlers (e.g., annotated with {@code @HandleCommand}, {@code @HandleQuery}, etc.)</li>
 *     <li>{@link Stateful} handlers — persisted and associated via {@link Association}</li>
 *     <li>{@link SocketEndpoint} handlers — WebSocket-based interaction handlers</li>
 *     <li>{@link TrackSelf} annotated classes — handlers for self-tracking message types</li>
 * </ul>
 *
 * <h2>Customization</h2>
 * The factory is configured with the following pluggable components:
 * <ul>
 *     <li>A {@link MessageType} indicating the type of messages it supports (e.g., COMMAND, QUERY)</li>
 *     <li>A {@link HandlerDecorator} used to wrap all created handlers with additional behavior</li>
 *     <li>A list of {@link ParameterResolver}s to inject method parameters during handler invocation</li>
 *     <li>A {@link MessageFilter} that determines whether a message is applicable to a handler method</li>
 *     <li>A {@link HandlerRepository} supplier for managing persisted state in {@code @Stateful} handlers</li>
 *     <li>A {@link RepositoryProvider} for shared caching of handler state (e.g., in {@code SocketEndpointHandler})</li>
 * </ul>
 *
 * <h2>Handler Resolution Process</h2>
 * The factory inspects the provided target object (or class) and applies the following logic:
 * <ol>
 *     <li>If the target is annotated with {@link Stateful}, a {@link StatefulHandler} is created</li>
 *     <li>If the target is annotated with {@link SocketEndpoint}, a {@link SocketEndpointHandler} is created</li>
 *     <li>If the target is annotated with {@link TrackSelf}, a handler is created with a filter ensuring messages are routed to matching payload types</li>
 *     <li>Otherwise, a default handler is created using {@link DefaultHandler}</li>
 * </ol>
 *
 * <h2>Decorator Chaining</h2>
 * Any additional {@link HandlerInterceptor}s passed at creation are composed with the default decorator
 * and applied to the resulting handler.
 *
 * <h2>Search-Specific Filtering</h2>
 * For {@link MessageType#DOCUMENT} and {@link MessageType#CUSTOM}, additional filters like
 * {@link HandleDocumentFilter} and {@link HandleCustomFilter} are applied automatically.
 *
 * <p>
 * This class is the main entry point for reflective handler generation in Flux Capacitor. It is used by both local
 * and tracking-based handler registries to resolve method targets dynamically.
 *
 * @see HandlerFactory
 * @see HandlerConfiguration
 * @see HandlerInspector
 * @see StatefulHandler
 * @see SocketEndpointHandler
 * @see DefaultHandler
 */
@AllArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {

    private final MessageType messageType;
    private final HandlerDecorator defaultDecorator;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final MessageFilter<? super DeserializingMessage> messageFilter;
    private final Class<? extends Annotation> handlerAnnotation;
    private final Function<Class<?>, HandlerRepository> handlerRepositorySupplier;
    private final RepositoryProvider repositoryProvider;

    private final Set<StaticFileHandler> staticFileHandlers = ConcurrentHashMap.newKeySet();

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
        Class<?> targetClass = asClass(target);
        HandlerDecorator handlerDecorator =
                ObjectUtils.concat(extraInterceptors.stream(), Stream.of(defaultDecorator))
                        .reduce(HandlerDecorator::andThen).orElseThrow();
        return Optional.of(handlerAnnotation)
                .map(a -> HandlerConfiguration.<DeserializingMessage>builder().methodAnnotation(a)
                        .handlerFilter(handlerFilter).messageFilter(messageFilter).build())
                .filter(config -> isHandler(targetClass, config))
                .map(config -> buildHandler(target, config))
                .map(handlerDecorator::wrap);
    }

    protected boolean isHandler(Class<?> targetClass,
                                HandlerConfiguration<?> handlerConfiguration) {
        return hasHandlerMethods(targetClass, handlerConfiguration) || StaticFileHandler.isHandler(targetClass);
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
        return createDefaultHandler(target, m -> target, config);
    }

    protected Handler<DeserializingMessage> createDefaultHandler(
            Object target, Function<DeserializingMessage, ?> targetSupplier,
            HandlerConfiguration<DeserializingMessage> config) {
        Class<?> targetClass = asClass(target);
        Handler<DeserializingMessage> handler
                = new DefaultHandler<>(targetClass, targetSupplier, createHandlerMatcher(target, config));
        if (messageType == MessageType.WEBREQUEST) {
            for (StaticFileHandler h : StaticFileHandler.forTargetClass(targetClass)) {
                if (staticFileHandlers.add(h)) {
                    handler = handler.or(createDefaultHandler(h, m -> h, config));
                }
            }
        }
        return handler;
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
            Object target, HandlerConfiguration<DeserializingMessage> config) {
        return switch (messageType) {
            case WEBREQUEST -> WebHandlerMatcher.create(target, parameterResolvers, config);
            default -> HandlerInspector.inspect(ReflectionUtils.asClass(target), parameterResolvers, config);
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
