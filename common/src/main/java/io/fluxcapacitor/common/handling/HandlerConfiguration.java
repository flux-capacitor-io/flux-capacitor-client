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

package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.function.BiPredicate;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class HandlerConfiguration<M> {
    Class<? extends Annotation> methodAnnotation;
    @Default boolean invokeMultipleMethods = false;
    @Default BiPredicate<Class<?>, Executable> handlerFilter = (c, e) -> true;
    @Default BiPredicate<M, Annotation> messageFilter = (m, a) -> true;

    public boolean methodMatches(Class<?> c, Executable e) {
        return Optional.ofNullable(methodAnnotation).map(a -> getAnnotation(e).isPresent()).orElse(true)
                && handlerFilter.test(c, e);
    }

    public Optional<? extends Annotation> getAnnotation(Executable e) {
        return ReflectionUtils.getMethodAnnotation(e, methodAnnotation);
    }
}
