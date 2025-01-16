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

@AllArgsConstructor
public class WebPayloadParameterResolver implements ParameterResolver<HasMessage> {
    private final boolean validatePayload;
    private final boolean authoriseUser;

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> {
            Object payload = m.getPayloadAs(p.getType());
            if (payload != null && validatePayload) {
                assertValid(payload);
            }
            if (authoriseUser) {
                if (payload != null) {
                    assertAuthorized(payload.getClass(), User.getCurrent());
                }
            }
            return payload;
        };
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return ReflectionUtils.isOrHas(methodAnnotation, HandleWeb.class);
    }
}
