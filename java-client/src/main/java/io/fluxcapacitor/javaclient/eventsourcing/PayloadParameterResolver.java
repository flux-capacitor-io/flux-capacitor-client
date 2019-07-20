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

package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class PayloadParameterResolver implements ParameterResolver<Message> {
    @Override
    public Function<Message, Object> resolve(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return Message::getPayload;
        }
        return null;
    }
    
    @Override
    public boolean matches(Parameter p, Message value) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return p.getType().isAssignableFrom(value.getPayload().getClass());
        }
        return false;
    }

}
