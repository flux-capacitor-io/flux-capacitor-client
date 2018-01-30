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

package io.fluxcapacitor.javaclient.gateway.correlation;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.gateway.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handler.HandlerInterceptor;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.function.Function;

@RequiredArgsConstructor
public class CorrelatingInterceptor implements HandlerInterceptor, DispatchInterceptor {

    private final Collection<? extends CorrelationDataProvider> correlationDataProviders;
    private DeserializingMessage currentMessage;

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return message -> {
            currentMessage = message;
            try {
                return function.apply(message);
            } finally {
                currentMessage = null;
            }
        };
    }

    @Override
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function) {
        return message -> {
            if (currentMessage != null) {
                correlationDataProviders.forEach(p -> message.getMetadata().putAll(p.fromMessage(currentMessage)));
            }
            return function.apply(message);
        };
    }
}
