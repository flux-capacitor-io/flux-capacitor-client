/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.reflect.Executable;
import java.util.function.BiPredicate;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class HandlerConfiguration<T> {

    @Default boolean invokeMultipleMethods = false;
    @Default BiPredicate<Class<?>, Executable> handlerFilter = (c, e) -> true;
    @Default MethodInvokerFactory<T> invokerFactory = MethodHandlerInvoker::new;

    @SuppressWarnings("unchecked")
    public static <T> HandlerConfiguration<T> defaultHandlerConfiguration() {
        return (HandlerConfiguration<T>) builder().build();
    }

    public static <T> HandlerConfiguration<T> localHandlerConfiguration() {
        return HandlerConfiguration.<T>builder().build();
    }

}
