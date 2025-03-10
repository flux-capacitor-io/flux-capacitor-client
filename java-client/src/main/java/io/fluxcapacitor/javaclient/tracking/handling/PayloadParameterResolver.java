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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

public class PayloadParameterResolver implements ParameterResolver<HasMessage> {
    @Override
    public boolean matches(Parameter p, Annotation methodAnnotation, HasMessage value) {
        return p.getType().isAssignableFrom(value.getPayloadClass());
    }

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return HasMessage::getPayload;
    }

    @Override
    public boolean filterMessage(HasMessage message, Parameter parameter) {
        return message.getPayload() != null || ReflectionUtils.isNullable(parameter); //may be the case after upcasting
    }

    @Override
    public boolean determinesSpecificity() {
        return true;
    }
}
