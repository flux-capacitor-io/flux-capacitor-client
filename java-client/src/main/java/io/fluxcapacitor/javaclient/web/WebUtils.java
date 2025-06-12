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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import jakarta.annotation.Nullable;
import lombok.NonNull;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.concat;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAndParentPackages;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Utility methods for working with web request and response data, including cookies, headers, and handler annotations.
 * <p>
 * This class supports parsing and formatting of HTTP cookie headers, case-insensitive HTTP header maps, and discovery
 * of {@link WebPattern} annotations on handler methods annotated with {@link HandleWeb}.
 */
public class WebUtils {
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^/}]+)}");

    /**
     * Returns a properly formatted {@code Set-Cookie} header value for the given cookie.
     * <p>
     * The result includes standard attributes such as {@code Domain}, {@code Path}, {@code Max-Age}, {@code HttpOnly},
     * and {@code Secure} if they are set on the cookie.
     *
     * @param cookie the cookie to format (must not be {@code null})
     * @return a header string suitable for a {@code Set-Cookie} response header
     */
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

    /**
     * Returns a formatted string for the {@code Cookie} request header containing the given cookie.
     *
     * @param cookie the cookie to encode (must not be {@code null})
     * @return a header string suitable for inclusion in a {@code Cookie} request header
     */
    public static String toRequestHeaderString(@NonNull HttpCookie cookie) {
        return cookie.getName() + "=" + URLEncoder.encode(cookie.getValue(), StandardCharsets.UTF_8);
    }

    /**
     * Parses a {@code Cookie} request header string into a list of {@link HttpCookie} instances.
     * <p>
     * The input is expected to contain one or more name-value pairs separated by semicolons.
     *
     * @param cookieHeader the value of the {@code Cookie} header, or {@code null}
     * @return a list of parsed {@link HttpCookie} instances (empty if input is {@code null})
     */
    public static List<HttpCookie> parseRequestCookieHeader(String cookieHeader) {
        return cookieHeader == null ? List.of() : Arrays.stream(cookieHeader.split(";")).flatMap(c -> {
            var parts = c.trim().split("=", 2);
            return parts.length == 2 ? Stream.of(
                    new HttpCookie(parts[0].trim(), parts[1].trim().replaceAll("^\"|\"$", ""))) :
                    Stream.empty();
        }).toList();
    }

    /**
     * Parses a list of {@code Set-Cookie} header values into a list of {@link HttpCookie} instances.
     * <p>
     * Each value in the input list should be a properly formatted {@code Set-Cookie} header line.
     *
     * @param setCookieHeaders the list of {@code Set-Cookie} header values, or {@code null}
     * @return a list of parsed {@link HttpCookie} instances (empty if input is {@code null})
     */
    public static List<HttpCookie> parseResponseCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders == null ? List.of()
                : setCookieHeaders.stream().flatMap(h -> HttpCookie.parse(h).stream()).toList();
    }

    /**
     * Returns all {@link WebPattern} instances declared on the given method.
     * <p>
     * This inspects all {@link HandleWeb} annotations on the method and resolves any declared {@link WebParameters} to
     * extract associated patterns.
     *
     * @param method the method to inspect
     * @return a list of {@link WebPattern} instances associated with the method
     */
    public static List<WebPattern> getWebPatterns(Executable method) {
        return ReflectionUtils.getMethodAnnotations(method, HandleWeb.class)
                .stream().flatMap(a -> ReflectionUtils.getAnnotationAs(a, HandleWeb.class, WebParameters.class)
                        .stream().flatMap(WebParameters::getWebPatterns)).toList();
    }

    /**
     * Returns all {@link WebPattern} instances declared on the given method in the given target class.
     * <p>
     * This inspects all {@link HandleWeb} annotations on the method and resolves any declared {@link WebParameters} to
     * extract associated patterns.
     *
     * @param targetClass the target class
     * @param handler     the handler instance (may be {@code null})
     * @param method      the method to inspect
     * @return a list of {@link WebPattern} instances associated with the method
     */
    public static List<WebPattern> getWebPatterns(Class<?> targetClass, @Nullable Object handler, Executable method) {
        String root = getHandlerPath(targetClass, handler, method);
        return ReflectionUtils.getMethodAnnotations(method, HandleWeb.class)
                .stream().flatMap(a -> ReflectionUtils.getAnnotationAs(a, HandleWeb.class, WebParameters.class)
                        .stream().flatMap(webParameters -> webParameters.getWebPatterns(root))).toList();
    }

    /**
     * Computes the complete {@link Path @Path} value for the given handler. {@code targetClass} is required;
     * {@code handler} and {@code method} are not. The path value is determined by inspecting the following elements:
     * <ul>
     *     <li>sub-packages</li>
     *     <li>class package</li>
     *     <li>target class</li>
     *     <li>handler properties (if the given handler is not null)</li>
     *     <li>handler method (if the given method is not null)</li>
     * </ul>
     * <p>
     * {@code @Path} values are joined together in the order listed above, with a slash as delimiter. If any of
     * the {@code @Path} values start with a slash, the chain is reset.
     */
    public static String getHandlerPath(Class<?> targetClass, @Nullable Object handler, @Nullable Executable method) {
        var mapper = pathValues();
        List<String> hierarchy = concat(
                getPackageAndParentPackages(targetClass.getPackage()).reversed().stream().flatMap(mapper),
                Stream.of(targetClass).flatMap(mapper),
                getAnnotatedProperty(handler, Path.class).stream()
                        .filter(p -> getAnnotation(p, Path.class).map(Path::value)
                                             .filter(String::isBlank).isPresent() && getAnnotation(p, HandleWeb.class).isEmpty())
                        .flatMap(a -> getAnnotatedPropertyValue(handler, Path.class).map(Object::toString).stream()),
                Optional.ofNullable(method).stream().flatMap(mapper)).toList();
        return hierarchy.stream().reduce((a, b) -> b.startsWith("/") ? b : concatenateUrlParts(a, b)).orElse("");
    }

    static Function<AnnotatedElement, Stream<String>> pathValues() {
        return element -> getAnnotation(element, Path.class).stream().map(Path::value).map(s -> {
            if (s.isBlank()) {
                return switch (element) {
                    case Class<?> c -> ReflectionUtils.getSimpleName(c.getPackage());
                    case Package p -> ReflectionUtils.getSimpleName(p);
                    case Executable m -> ReflectionUtils.getSimpleName(m.getDeclaringClass());
                    default -> null;
                };
            }
            return s;
        }).filter(Objects::nonNull);
    }

    /**
     * Returns a new case-insensitive header map, with keys compared ignoring case.
     *
     * @return an empty case-insensitive {@code Map} for headers
     */
    public static Map<String, List<String>> emptyHeaderMap() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Converts the input map into a case-insensitive header map.
     * <p>
     * Keys in the result will be case-insensitive, with the contents copied from the input map.
     *
     * @param input the input map
     * @return a case-insensitive map containing the same entries
     */
    public static Map<String, List<String>> asHeaderMap(Map<String, List<String>> input) {
        Map<String, List<String>> result = emptyHeaderMap();
        result.putAll(input);
        return result;
    }

    /**
     * Checks if the given path contains a named path parameter.
     */
    public static boolean hasPathParameter(String path) {
        return PATH_PARAM_PATTERN.matcher(path).find();
    }

    /**
     * Extracts all named path parameters from the given path.
     * <p>
     * Example usage:
     * <pre>{@code
     * E.g. List<String> params = extractPathParameters("/games/{gameId}/refund/{orderId}");
     *      // → ["gameId", "orderId"]
     * }</pre>
     */
    public static List<String> extractPathParameters(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        Set<String> parameters = new LinkedHashSet<>();
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }
        return new ArrayList<>(parameters);
    }

    /**
     * Replaces named path parameter with provided value.
     * <p>
     * Example usage:
     * <pre>{@code
     * replacePathParameter("/users/{userId}/games/{gameId}", "userId", "123");
     *      // → "/users/123/games/{gameId}"
     * }</pre>
     */
    public static String replacePathParameter(String path, String parameterName, String value) {
        if (value == null) {
            return path;
        }
        return path.replaceAll("\\{" + Pattern.quote(parameterName) + "}", Matcher.quoteReplacement(value));
    }

    /**
     * Concatenates multiple segments of a URL, ensuring that:
     * <ul>
     *     <li>Double slashes in schemes like {@code https://} are preserved</li>
     *     <li>Duplicate slashes between path segments are removed</li>
     *     <li>Null or empty segments are skipped</li>
     * </ul>
     *
     * @param parts URL parts, e.g., {@code "https://", "example.org", "/api/", "/v1/"}
     * @return A clean, properly concatenated URL, e.g. {@code "https://example.org/api/v1/"}
     */
    public static String concatenateUrlParts(String... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        boolean first = true;
        boolean startsWithSlash = false;

        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (first) {
                if (part.matches("^[a-zA-Z][a-zA-Z\\d+\\-.]*://.*")) {
                    cleaned.add(part.replaceAll("/+$", ""));
                } else {
                    startsWithSlash = part.startsWith("/");
                    cleaned.add(part.replaceAll("^/+", "").replaceAll("/+$", ""));
                }
                first = false;
            } else {
                cleaned.add(part.replaceAll("^/+", "").replaceAll("/+$", ""));
            }
        }

        String joined = String.join("/", cleaned);
        if (startsWithSlash || (!joined.isBlank() && !joined.startsWith("/")
                                && !joined.equals("*") && !joined.contains("://"))) {
            joined = "/" + joined;
        }
        return joined;
    }
}
