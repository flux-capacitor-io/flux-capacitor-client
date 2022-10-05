/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

public class WebPayloadParameterResolver implements ParameterResolver<HasMessage> {
    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return ReflectionUtils.isOrHas(methodAnnotation, HandleWeb.class) ? m -> m.getPayloadAs(p.getType()) : null;
    }
}
