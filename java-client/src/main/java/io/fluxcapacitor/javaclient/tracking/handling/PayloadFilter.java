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

import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import lombok.Value;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

public class PayloadFilter implements MessageFilter<HasMessage> {

    private final Function<Class<? extends Annotation>, Function<Executable, HandleAnnotation>> allowedClassProvider
            = memoize(a -> memoize(e -> ReflectionUtils.getAnnotationAs(e, a, HandleAnnotation.class)
            .orElse(null)));

    @Override
    public boolean test(HasMessage message, Executable executable, Class<? extends Annotation> handlerAnnotation) {
        Class<?> payloadClass = message.getPayloadClass();
        return Optional.ofNullable(allowedClassProvider.apply(handlerAnnotation).apply(executable))
                .map(a -> a.getAllowedClasses().isEmpty() || a.getAllowedClasses().stream()
                        .anyMatch(c -> c.isAssignableFrom(payloadClass))).orElse(true);
    }

    @Override
    public Optional<Class<?>> getLeastSpecificAllowedClass(Executable executable,
                                                           Class<? extends Annotation> handlerAnnotation) {
        return Optional.ofNullable(allowedClassProvider.apply(handlerAnnotation).apply(executable))
                .flatMap(a -> a.getAllowedClasses().stream()
                        .max(ReflectionUtils.getClassSpecificityComparator()));
    }

    @Value
    static class HandleAnnotation {
        List<Class<?>> allowedClasses;
    }
}
