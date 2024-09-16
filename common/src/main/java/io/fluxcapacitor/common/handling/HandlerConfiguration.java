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

package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class HandlerConfiguration<M> {
    Class<? extends Annotation> methodAnnotation;
    @Default
    boolean invokeMultipleMethods = false;
    @Default
    HandlerFilter handlerFilter = (c, e) -> true;
    @Default
    MessageFilter<? super M> messageFilter = (m, e, handlerAnnotation) -> true;

    public boolean methodMatches(Class<?> c, Executable e) {
        return isEnabled(e) && handlerFilter.test(c, e);
    }

    @SneakyThrows
    boolean isEnabled(Executable e) {
        if (methodAnnotation == null) {
            return true;
        }
        var annotation = getAnnotation(e).orElse(null);
        if (annotation == null) {
            return false;
        }
        Optional<Method> match = Arrays.stream(annotation.annotationType().getMethods())
                .filter(m -> m.getName().equals("disabled")).findFirst();
        if (match.isPresent()) {
            var result = (boolean) match.get().invoke(annotation);
            return !result;
        }
        return true;
    }

    public Optional<? extends Annotation> getAnnotation(Executable e) {
        return ofNullable(methodAnnotation).flatMap(a -> ReflectionUtils.getMethodAnnotation(e, methodAnnotation));
    }
}
