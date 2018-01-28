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

package io.fluxcapacitor.javaclient.gateway.interceptors;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.gateway.DispatchInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;

@AllArgsConstructor
public class MessageRoutingInterceptor implements DispatchInterceptor {
    @Override
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function) {
        return m -> getAnnotatedPropertyValue(m.getPayload(), RoutingKey.class).map(Object::toString)
                .map(ConsistentHashing::computeSegment).map(s -> {
                    SerializedMessage serializedMessage = function.apply(m);
                    serializedMessage.setSegment(s);
                    return serializedMessage;
                }).orElse(function.apply(m));
    }
}
