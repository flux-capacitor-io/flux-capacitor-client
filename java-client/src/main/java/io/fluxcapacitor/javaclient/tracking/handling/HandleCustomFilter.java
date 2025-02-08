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
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

public class HandleCustomFilter implements MessageFilter<DeserializingMessage> {

    Function<Executable, Optional<HandleCustom>> handleCustomFunction =
            memoize(e -> ReflectionUtils.getAnnotation(e, HandleCustom.class));

    @Override
    public boolean test(DeserializingMessage message, Executable executable,
                        Class<? extends Annotation> handlerAnnotation) {
        return handleCustomFunction.apply(executable).map(c -> Objects.equals(c.value(), message.getTopic()))
                .orElse(false);
    }
}
