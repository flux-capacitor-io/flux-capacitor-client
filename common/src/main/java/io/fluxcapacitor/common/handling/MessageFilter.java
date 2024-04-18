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

import lombok.NonNull;

import java.lang.reflect.Executable;
import java.util.Optional;

@FunctionalInterface
public interface MessageFilter<M> {
    boolean test(M message, Executable executable);

    default Optional<Class<?>> getLeastSpecificAllowedClass(Executable executable) {
        return Optional.empty();
    }

    default MessageFilter<M> and(@NonNull MessageFilter<? super M> second) {
        var first = this;
        return new MessageFilter<>() {
            @Override
            public boolean test(M m, Executable e) {
                return first.test(m, e) && second.test(m, e);
            }

            @Override
            public Optional<Class<?>> getLeastSpecificAllowedClass(Executable executable) {
                return first.getLeastSpecificAllowedClass(executable).or(() -> second.getLeastSpecificAllowedClass(executable));
            }
        };
    }
}
