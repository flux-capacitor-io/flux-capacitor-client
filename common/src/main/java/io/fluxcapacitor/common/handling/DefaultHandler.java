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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Function;

@AllArgsConstructor
@Slf4j
public class DefaultHandler<M> implements Handler<M> {
    private final Class<?> targetClass;
    private final Function<M, ?> targetSupplier;
    private final HandlerMatcher<Object, M> handlerMatcher;

    @Override
    public Class<?> getTargetClass() {
        return targetClass;
    }

    @Override
    public Optional<HandlerInvoker> getInvoker(M message) {
        return handlerMatcher.getInvoker(targetSupplier.apply(message), message);
    }

    @Override
    public String toString() {
        String simpleName = targetClass.getSimpleName();
        return String.format("\"%s\"", simpleName.isEmpty() ? "DefaultHandler" : simpleName);
    }
}
