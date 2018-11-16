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

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class PayloadParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return DeserializingMessage::getPayload;
        }
        return null;
    }

    @Override
    public boolean matches(Parameter p, DeserializingMessage value) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            Class<?> payloadClass;
            try {
                payloadClass = value.getPayloadClass();
            } catch (Exception e) {
                return false; //class may be unknown, in that case we simply want to ignore the message
            }
            return p.getType().isAssignableFrom(payloadClass);
        }
        return false;
    }
}
