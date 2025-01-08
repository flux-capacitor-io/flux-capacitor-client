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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ParameterRegistry;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.web.DefaultWebRequestContext.getWebRequestContext;
import static io.micrometer.common.util.StringUtils.isBlank;

@AllArgsConstructor
public class WebParamParameterResolver implements ParameterResolver<HasMessage> {

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> {
            WebRequestContext context = getWebRequestContext((DeserializingMessage) m);
            Optional<ParamField> field = ReflectionUtils.getAnnotationAs(p, WebParam.class, ParamField.class);
            return field.map(f -> {
                        String value = f.getValue();
                        String name;
                        if (isBlank(value)) {
                            if (p.isNamePresent()) {
                                name = p.getName();
                            } else {
                                var registry = ParameterRegistry.of(p.getDeclaringExecutable().getDeclaringClass());
                                name = registry.getParameterName(p);
                            }
                        } else {
                            name = value;
                        }
                        return context.getParameter(f.getType(), name);
                    }).map(v -> v.as(p.getType())).orElse(null);
        };
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return value instanceof DeserializingMessage m && m.getMessageType() == MessageType.WEBREQUEST
               && ReflectionUtils.isAnnotationPresent(parameter, WebParam.class);
    }

    @Value
    static class ParamField {
        String value;
        WebParameterType type;
    }
}
