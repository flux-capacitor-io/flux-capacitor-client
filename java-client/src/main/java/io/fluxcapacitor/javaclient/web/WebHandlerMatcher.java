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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerMatcher;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.web.internal.WebUtilsInternal;
import io.jooby.Router;

import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.web.DefaultWebRequestContext.getWebRequestContext;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

/**
 * Specialized {@link HandlerMatcher} that routes {@link DeserializingMessage}s of type {@link MessageType#WEBREQUEST}
 * to matching handler methods based on annotated URI patterns, HTTP methods, and optional origins.
 * <p>
 * This matcher is created internally by the {@link io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory} when
 * registering a handler class that contains methods annotated for web request handling (e.g., {@code @HandleWeb}).
 *
 * <h2>Routing Logic</h2>
 * The matcher builds a {@link Router} that maps:
 * <ul>
 *   <li>HTTP method (e.g., GET, POST)</li>
 *   <li>Normalized path (optionally prefixed by {@code @Path} at class or package level)</li>
 *   <li>Optional request origin (e.g., scheme and host) when specified in the handler method</li>
 * </ul>
 * During matching, the {@link DefaultWebRequestContext} is used to extract the URI path, method, and origin
 * from the incoming request metadata.
 *
 * <h2>WebPattern Matching</h2>
 * Each handler method may be associated with one or more {@link WebPattern}s, derived from {@link WebParameters}
 * annotations. These patterns define the matchable paths and methods.
 *
 * <h2>Support for @Path Annotations</h2>
 * This matcher also respects {@code @Path} annotations on the method, declaring class, or package level,
 * combining those with the {@code WebPattern#getPath()} when routing requests.
 *
 * <h2>Fallback to ANY Method</h2>
 * If no handler matches the exact request method, but any handlers exist that declare {@code HttpRequestMethod.ANY},
 * these are checked as a fallback.
 *
 * @see HandlerMatcher
 * @see WebPattern
 * @see io.fluxcapacitor.javaclient.web.WebRequest
 * @see io.fluxcapacitor.javaclient.web.WebUtils#getWebPatterns
 */
public class WebHandlerMatcher implements HandlerMatcher<Object, DeserializingMessage> {
    private final Router router = WebUtilsInternal.router();
    private final boolean hasAnyHandlers;

    public static WebHandlerMatcher create(
            Class<?> c, List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
            HandlerConfiguration<DeserializingMessage> config) {
        var matchers = concat(getAllMethods(c).stream(), stream(c.getDeclaredConstructors()))
                .filter(m -> config.methodMatches(c, m))
                .flatMap(m -> Stream.of(new MethodHandlerMatcher<>(m, c, parameterResolvers, config))).toList();
        return new WebHandlerMatcher(matchers);
    }

    protected WebHandlerMatcher(List<MethodHandlerMatcher<DeserializingMessage>> methodHandlerMatchers) {
        Map<String, Router> subRouters = new HashMap<>();
        boolean hasAnyHandlers = false;
        for (MethodHandlerMatcher<DeserializingMessage> m : methodHandlerMatchers) {
            String root = ReflectionUtils.<Path>getMethodAnnotation(m.getExecutable(), Path.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(m.getTargetClass(), Path.class)))
                    .or(() -> getPackageAnnotation(m.getTargetClass().getPackage(), Path.class))
                    .map(Path::value)
                    .map(p -> p.endsWith("//") || !p.endsWith("/") ? p : p.substring(0, p.length() - 1))
                    .orElse("");
            List<WebPattern> webPatterns = WebUtils.getWebPatterns(m.getExecutable());

            for (WebPattern pattern : webPatterns) {
                String origin = pattern.getOrigin();
                var router = origin == null ? this.router : subRouters.computeIfAbsent(origin, __ -> WebUtilsInternal.router());
                if (HttpRequestMethod.ANY.equals(pattern.getMethod())) {
                    hasAnyHandlers = true;
                }
                router.route(pattern.getMethod(), root + pattern.getPath(), ctx -> m);
            }
        }
        subRouters.forEach((origin, subRouter) -> this.router.mount(ctx -> {
            if (ctx instanceof DefaultWebRequestContext context) {
                return Objects.equals(origin, context.getOrigin());
            }
            throw new UnsupportedOperationException("Unknown context class: " + ctx.getClass());
        }, subRouter));
        this.hasAnyHandlers = hasAnyHandlers;
    }

    @Override
    public boolean canHandle(DeserializingMessage message) {
        return methodMatcher(message).map(m -> m.canHandle(message)).orElse(false);
    }

    @Override
    public Stream<Executable> matchingMethods(DeserializingMessage message) {
        return methodMatcher(message).stream().flatMap(m -> m.matchingMethods(message));
    }

    @Override
    public Optional<HandlerInvoker> getInvoker(Object target, DeserializingMessage message) {
        return methodMatcher(message).flatMap(m -> m.getInvoker(target, message));
    }

    @SuppressWarnings("unchecked")
    protected Optional<MethodHandlerMatcher<DeserializingMessage>> methodMatcher(DeserializingMessage message) {
        if (message.getMessageType() != MessageType.WEBREQUEST) {
            return Optional.empty();
        }
        DefaultWebRequestContext context = getWebRequestContext(message);
        Optional<Router.Match> match = Optional.of(router.match(context)).filter(Router.Match::matches);
        if (match.isEmpty() && hasAnyHandlers) {
            match = Optional.of(router.match(context.withMethod(HttpRequestMethod.ANY))).filter(Router.Match::matches);
        }
        return match.map(m -> (MethodHandlerMatcher<DeserializingMessage>) m.execute(context));
    }

}
