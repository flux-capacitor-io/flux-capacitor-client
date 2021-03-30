/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import lombok.Value;

import java.lang.reflect.Parameter;
import java.util.function.Function;

/*
    Should always be the last parameter resolver, since it matches everything in the first parameter
 */
@Value
public class UntypedPayloadParameterResolver implements ParameterResolver<DeserializingMessage> {

    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            if(byte[].class.equals(p.getType())){
                return m -> m.getSerializedObject().getData().getValue();
            }
            if(String.class.equals(p.getType())){
                return m -> {
                    String s = new String(m.getSerializedObject().getData().getValue());
                    return s;
                };
            }
            return m -> m.getPayloadAs(p.getType());
        }
        return null;
    }

    @Override
    public boolean matches(Parameter parameter, DeserializingMessage value) {
        return parameter.getDeclaringExecutable().getParameters()[0] == parameter;
    }
}
