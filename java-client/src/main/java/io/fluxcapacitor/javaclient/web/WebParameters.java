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

import java.util.Arrays;
import java.util.stream.Stream;

@Value
public class WebParameters {
    String[] value;
    HttpRequestMethod[] method;
    boolean disabled;

    public Stream<WebPattern> getWebPatterns() {
        Stream<HttpRequestMethod> methodStream = method.length == 0
                ? Arrays.stream(HttpRequestMethod.values()) : Arrays.stream(method);
        return methodStream.flatMap(method -> switch (value.length) {
            case 0 -> Stream.of(new WebPattern("", method));
            case 1 -> Stream.of(new WebPattern(value[0], method));
            default -> Arrays.stream(value).map(v -> new WebPattern(v, method));
        });
    }
}