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
import lombok.NonNull;

import java.lang.reflect.Executable;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class WebUtils {
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
        return cookieHeader == null ? List.of() : Arrays.stream(cookieHeader.split(";")).flatMap(c -> {
            var parts = c.trim().split("=", 2);
            return parts.length == 2 ? Stream.of(
                    new HttpCookie(parts[0].trim(), parts[1].trim().replaceAll("^\"|\"$", ""))) :
                    Stream.empty();
        }).toList();
    }

    public static List<HttpCookie> parseResponseCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders == null ? List.of()
                : setCookieHeaders.stream().flatMap(h -> HttpCookie.parse(h).stream()).toList();
    }

    public static List<WebPattern> getWebPatterns(Executable method) {
        return ReflectionUtils.getMethodAnnotations(method, HandleWeb.class)
                .stream().flatMap(a -> ReflectionUtils.getAnnotationAs(a, HandleWeb.class, WebParameters.class)
                        .stream().flatMap(WebParameters::getWebPatterns)).toList();
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
}
