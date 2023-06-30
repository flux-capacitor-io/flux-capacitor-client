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

package io.fluxcapacitor.common.handling;

import lombok.NonNull;

import java.lang.reflect.Executable;

@FunctionalInterface
public interface HandlerFilter {

    HandlerFilter ALWAYS_HANDLE = (t, e) -> true;

    boolean test(Class<?> ownerType, Executable executable);

    default HandlerFilter and(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) && other.test(o, e);
    }

    default HandlerFilter negate() {
        return (o, e) -> !test(o, e);
    }

    default HandlerFilter or(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) || other.test(o, e);
    }
}
