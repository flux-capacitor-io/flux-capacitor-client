/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.function.Function;

@FunctionalInterface
public interface HandlerInterceptor {
    Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function);

    default HandlerInterceptor merge(HandlerInterceptor outerInterceptor) {
        return f -> outerInterceptor.interceptHandling(interceptHandling(f));
    }
}