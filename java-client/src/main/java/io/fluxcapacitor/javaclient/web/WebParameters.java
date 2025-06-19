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

import lombok.Value;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.web.WebUtils.concatenateUrlParts;

/**
 * Internal configuration holder used to expand the URI and method mappings of a web request handler method.
 * <p>
 * This class supports handlers that match multiple URI patterns and/or HTTP methods. It is typically used in
 * conjunction with the {@code @HandleWeb} annotation, where a single method can be annotated with multiple
 * {@link WebParameters} to define various acceptable request routes.
 *
 * <p>Each {@code WebParameters} instance includes:
 * <ul>
 *     <li>A list of URI patterns (e.g. {@code /users}, {@code /users/{id}})</li>
 *     <li>A list of HTTP methods (e.g. {@code GET}, {@code POST})</li>
 *     <li>An optional {@code disabled} flag that disables this set of patterns</li>
 * </ul>
 *
 * <p>The {@link #getWebPatterns()} method expands this configuration into a {@link Stream} of
 * {@link WebPattern} instances—one for each combination of method and URI pattern.
 *
 * @see WebPattern
 * @see WebUtils#getWebPatterns(Executable)
 */
@Value
public class WebParameters {

    /**
     * URI patterns to match (can be empty for wildcard).
     */
    String[] value;

    /**
     * HTTP methods to match (e.g. GET, POST).
     */
    String[] method;

    /**
     * Whether this set of patterns is disabled.
     */
    boolean disabled;

    /**
     * Expands this configuration into a stream of {@link WebPattern} instances.
     * <p>
     * If no URI patterns are provided, a wildcard pattern ({@code ""}) is assumed.
     *
     * @return a stream of {@link WebPattern} combinations for method/URI
     */
    public Stream<WebPattern> getWebPatterns() {
        return getWebPatterns("");
    }

    /**
     * Expands this configuration into a stream of {@link WebPattern} instances.
     * <p>
     * If no URI patterns are provided, a wildcard pattern ({@code ""}) is assumed.
     *
     * @return a stream of {@link WebPattern} combinations for method/URI
     */
    public Stream<WebPattern> getWebPatterns(String rootPath) {
        Stream<String> methodStream = Arrays.stream(method);
        return methodStream.flatMap(method -> switch (value.length) {
            case 0 -> Stream.of(new WebPattern(concatenateUrlParts(rootPath, ""), method));
            case 1 -> Stream.of(new WebPattern(concatenateUrlParts(rootPath, value[0]), method));
            default -> Arrays.stream(value).map(v -> new WebPattern(concatenateUrlParts(rootPath, v), method));
        });
    }
}