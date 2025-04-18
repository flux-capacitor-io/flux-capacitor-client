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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import java.util.function.Function;
import java.util.stream.Stream;

@FunctionalInterface
public interface Caster<I, O> {

    default Stream<? extends O> cast(Stream<? extends I> input) {
        return cast(input, null);
    }

    Stream<? extends O> cast(Stream<? extends I> input, Integer desiredRevision);

    default <BEFORE, AFTER> Caster<BEFORE, AFTER> intercept(Function<BEFORE, ? extends I> before,
                                                            Function<? super O, AFTER> after) {
        return (inputStream, rev) -> cast(inputStream.map(before), rev).map(after);
    }

}
