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

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertAuthorized;
import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertValid;

/**
 * Resolves a method parameter from the payload of a {@link io.fluxcapacitor.javaclient.web.WebRequest}.
 * <p>
 * This resolver is only applied to methods annotated with {@link HandleWeb} or any of its meta-annotations (e.g.
 * {@link HandlePost}). It converts the deserialized payload to the method parameter's declared type.
 * <p>
 * Optionally, the resolver can enforce validation and authorization:
 * <ul>
 *   <li>If {@code validatePayload} is {@code true}, the resolved payload is validated using {@code assertValid()}.</li>
 *   <li>If {@code authoriseUser} is {@code true}, the current {@link User} must be authorized to execute the payload’s type via {@code assertAuthorized()}.</li>
 * </ul>
 *
 * @see HandleWeb
 */
@AllArgsConstructor
public class WebPayloadParameterResolver implements ParameterResolver<HasMessage> {
    private final boolean validatePayload;
    private final boolean authoriseUser;

    /**
     * Resolves the value for the given method parameter by converting the message payload to the expected parameter
     * type. If configured, it also validates and authorizes the payload.
     *
     * @param p                the method parameter to resolve
     * @param methodAnnotation the annotation on the handler method (typically {@code @HandleWeb})
     * @return a function that resolves the parameter from a {@link HasMessage} instance
     */
    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> {
            Object payload = m.getPayloadAs(p.getType());
            if (payload != null) {
                if (validatePayload) {
                    assertValid(payload);
                }
                if (authoriseUser) {
                    assertAuthorized(payload.getClass(), User.getCurrent());
                }
            }
            return payload;
        };
    }

    @Override
    public boolean filterMessage(HasMessage m, Parameter p) {
        if (authoriseUser) {
            Object payload = m.getPayloadAs(p.getType());
            if (payload != null) {
                try {
                    boolean authorized = assertAuthorized(payload.getClass(), User.getCurrent());
                    if (!authorized) {
                        //ignore silently if the user is not authorized and no exception should be thrown
                        return false;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ParameterResolver.super.filterMessage(m, p);
    }

    /**
     * Determines whether this resolver should be used for the given method parameter. This resolver is active for any
     * method annotated with {@link HandleWeb} or any annotation that is meta-annotated with it.
     *
     * @param parameter        the method parameter
     * @param methodAnnotation the annotation on the method
     * @param value            the incoming message
     * @return {@code true} if the resolver is applicable to this parameter
     */
    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return ReflectionUtils.isOrHas(methodAnnotation, HandleWeb.class);
    }
}
