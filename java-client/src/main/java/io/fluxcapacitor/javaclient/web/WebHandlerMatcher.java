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
import io.jooby.Router;
import io.jooby.internal.RouterImpl;

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

public class WebHandlerMatcher implements HandlerMatcher<Object, DeserializingMessage> {
    private final Router router = new RouterImpl();

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
                var router = origin == null ? this.router : subRouters.computeIfAbsent(origin, __ -> new RouterImpl());
                router.route(pattern.getMethod(), root + pattern.getPath(), ctx -> m);
            }
        }
        subRouters.forEach((origin, subRouter) -> this.router.mount(ctx -> {
            if (ctx instanceof DefaultWebRequestContext context) {
                return Objects.equals(origin, context.getOrigin());
            }
            throw new UnsupportedOperationException("Unknown context class: " + ctx.getClass());
        }, subRouter));
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
        var context = getWebRequestContext(message);
        return Optional.of(router.match(context)).filter(Router.Match::matches)
                .map(match -> (MethodHandlerMatcher<DeserializingMessage>) match.execute(context));
    }

}
