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

/**
 * Resolves method parameters in web handler methods based on meta-annotations derived from {@link WebParam}.
 *
 * <p>This resolver targets parameters in methods that handle {@link MessageType#WEBREQUEST} messages.
 * It looks for parameters annotated with a concrete annotation that is itself meta-annotated with {@code @WebParam},
 * such as {@code @PathParam}, {@code @QueryParam}, or other custom parameter annotations used in web APIs.
 *
 * <p>The resolver extracts the desired parameter value from the associated {@link WebRequestContext}
 * using the rules and source defined by the meta-annotation (e.g. from the path, query string, or headers).
 *
 * <p>To determine the parameter name to resolve, the following resolution strategy is used:
 * <ul>
 *   <li>If {@code @WebParam.value()} is set, it is used directly.</li>
 *   <li>Otherwise, if Java parameter names are available (compiled with {@code -parameters}) or if the method was compiled from Kotlin source, the parameter name is used.</li>
 *   <li>If neither is available, the name is resolved from a generated {@link ParameterRegistry} class.</li>
 * </ul>
 *
 * <p>If the parameter cannot be found in the request, {@code null} is returned.
 *
 * @see WebRequest
 * @see io.fluxcapacitor.common.api.Metadata
 * @see WebParam
 * @see WebParameterSource
 */
@AllArgsConstructor
public class WebParamParameterResolver implements ParameterResolver<HasMessage> {

    /**
     * Resolves the parameter value from a {@link WebRequestContext} using the metadata provided by a
     * parameter annotation that is meta-annotated with {@link WebParam}.
     *
     * <p>The parameter name is determined based on the annotation value, Java reflection, or a generated
     * {@link ParameterRegistry}, and the value is extracted from the request using the declared
     * {@link WebParameterSource} (e.g. QUERY, PATH, HEADER).
     *
     * @param p the method parameter to resolve
     * @param methodAnnotation the handler method annotation (not used here)
     * @return a function that resolves the argument from the incoming {@link HasMessage}
     */
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
                        return context.getParameter(name, f.getType());
                    }).map(v -> v.as(p.getType())).orElse(null);
        };
    }

    /**
     * Determines if this resolver is applicable to a given method parameter.
     *
     * <p>This returns {@code true} if:
     * <ul>
     *   <li>The incoming message is a {@link DeserializingMessage}</li>
     *   <li>The message type is {@link MessageType#WEBREQUEST}</li>
     *   <li>The parameter is annotated with an annotation that is meta-annotated with {@link WebParam}</li>
     * </ul>
     *
     * @param parameter the method parameter
     * @param methodAnnotation the enclosing method annotation (not used here)
     * @param value the message passed to the handler
     * @return {@code true} if the parameter is eligible for resolution
     */
    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return value instanceof DeserializingMessage m && m.getMessageType() == MessageType.WEBREQUEST
               && ReflectionUtils.isAnnotationPresent(parameter, WebParam.class);
    }

    @Value
    static class ParamField {
        String value;
        WebParameterSource type;
    }
}
