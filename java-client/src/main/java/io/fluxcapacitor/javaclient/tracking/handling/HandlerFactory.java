/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface HandlerFactory {

    default Optional<Handler<DeserializingMessage>> createHandler(Object target, String consumer, HandlerFilter handlerFilter,
                                                          HandlerInterceptor... handlerInterceptors) {
        return createHandler(target, consumer, handlerFilter, Arrays.asList(handlerInterceptors));
    }

    Optional<Handler<DeserializingMessage>> createHandler(Object target, String consumer, HandlerFilter handlerFilter,
                                                          List<HandlerInterceptor> handlerInterceptors);
}
