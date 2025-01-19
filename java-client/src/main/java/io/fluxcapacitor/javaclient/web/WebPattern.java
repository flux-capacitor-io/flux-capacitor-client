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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
public class WebPattern {
    static final Pattern uriPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    static Matcher uriMatcher(String uriString) {
        Matcher result = uriPattern.matcher(Optional.ofNullable(uriString).orElse(""));
        if (result.matches()) {
            return result;
        }
        throw new IllegalStateException("Malformed URI: '" + uriString + "'");
    }

    String uri;
    HttpRequestMethod method;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    Matcher matcher = uriMatcher(uri);
    @Getter(lazy = true)
    String path = Optional.ofNullable(getMatcher().group(5)).map(p -> p.startsWith("/") ? p : p.isBlank() ? "" : "/" + p).orElse("");
    @Getter(lazy = true)
    String origin = Optional.ofNullable(getMatcher().group(1))
            .map(scheme -> scheme + getMatcher().group(3)).orElse(null);
}
