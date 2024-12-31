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

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class WebUtils {
    private static final BiFunction<Executable, Class<? extends Annotation>, Predicate<HasMessage>> filterCache =
            memoize((e, a) -> {
                var declaringClass = e.getDeclaringClass();
                String root = ReflectionUtils.<Root>getMethodAnnotation(e, Root.class)
                        .or(() -> Optional.ofNullable(getTypeAnnotation(declaringClass, Root.class)))
                        .or(() -> getPackageAnnotation(declaringClass.getPackage(), Root.class))
                        .map(Root::value)
                        .map(p -> p.endsWith("//") || !p.endsWith("/") ? p : p.substring(0, p.length() - 1))
                        .orElse("");
                var handleWeb = WebUtils.getWebParameters(e, a).orElseThrow();
                Predicate<String> pathTest = Optional.of(root + handleWeb.getPath())
                        .map(SearchUtils::getGlobMatcher)
                        .<Predicate<String>>map(p -> s -> p.test(s.startsWith("/") || s.contains("://") ? s : "/" + s))
                        .orElse(p -> true);
                Predicate<String> methodTest = Optional.of(handleWeb.getMethod())
                        .<Predicate<String>>map(r -> p -> r.name().equals(p))
                        .orElse(p -> true);
                return msg -> {
                    String path = requireNonNull(msg.getMetadata().get("url"),
                                                 "Web request url is missing in the metadata of a WebRequest message");
                    String method = requireNonNull(msg.getMetadata().get("method"),
                                                   "Web request method is missing in the metadata of a WebRequest message");
                    return pathTest.test(path) && methodTest.test(method);
                };
            });

    public static String toResponseHeaderString(@NonNull HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(URLEncoder.encode(cookie.getValue(), StandardCharsets.UTF_8));
        if (!isBlank(cookie.getDomain())) {
            sb.append("; ").append("Domain=").append(cookie.getDomain());
        }
        if (!isBlank(cookie.getPath())) {
            sb.append("; ").append("Path=").append(cookie.getPath());
        }
        if (cookie.getMaxAge() != -1) {
            sb.append("; ").append("Max-Age=").append(cookie.getMaxAge());
        }
        if (cookie.isHttpOnly()) {
            sb.append("; ").append("HttpOnly");
        }
        if (cookie.getSecure()) {
            sb.append("; ").append("Secure");
        }
        return sb.toString();
    }

    public static String toRequestHeaderString(@NonNull HttpCookie cookie) {
        return cookie.getName() + "=" + URLEncoder.encode(cookie.getValue(), StandardCharsets.UTF_8);
    }

    public static List<HttpCookie> parseRequestCookieHeader(String cookieHeader) {
        return cookieHeader == null ? List.of() : Arrays.stream(cookieHeader.split(";")).map(c -> {
            var parts = c.trim().split("=");
            return new HttpCookie(parts[0].trim(), parts[1].trim().replaceAll("^\"|\"$", ""));
        }).collect(Collectors.toList());
    }

    public static List<HttpCookie> parseResponseCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders == null ? List.of()
                : setCookieHeaders.stream().flatMap(h -> HttpCookie.parse(h).stream()).toList();
    }

    public static Optional<WebParameters> getWebParameters(Executable method) {
        return getWebParameters(method, HandleWeb.class);
    }

    public static Optional<WebParameters> getWebParameters(
            Executable method, Class<? extends Annotation> annotationType) {
        return ReflectionUtils.getMethodAnnotation(method, annotationType)
                .flatMap(a -> ReflectionUtils.getAnnotationAs(a, HandleWeb.class, WebParameters.class));
    }

    public static String fixHeaderName(String name) {
        return name == null ? null : name.toLowerCase();
    }

    public static Map<String, List<String>> emptyHeaderMap() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    public static Map<String, List<String>> asHeaderMap(Map<String, List<String>> input) {
        Map<String, List<String>> result = emptyHeaderMap();
        result.putAll(input);
        return result;
    }

    public static MessageFilter<HasMessage> getWebRequestFilter() {
        return (message, executable, handlerAnnotation) -> filterCache.apply(
                executable, handlerAnnotation).test(message);
    }
}
