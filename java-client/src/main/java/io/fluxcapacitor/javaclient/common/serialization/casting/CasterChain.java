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

import io.fluxcapacitor.common.Registration;

import java.util.function.Function;
import java.util.stream.Stream;

public interface CasterChain<I, O> extends Caster<I, O> {
    Registration registerCasterCandidates(Object... candidates);

    default <BEFORE, AFTER> CasterChain<BEFORE, AFTER> intercept(Function<BEFORE, ? extends I> before,
                                                                 Function<? super O, AFTER> after) {
        return new CasterChain<>() {
            @Override
            public Stream<? extends AFTER> cast(Stream<? extends BEFORE> inputStream, Integer rev) {
                return CasterChain.this.cast(inputStream.map(before), rev).map(after);
            }

            @Override
            public Registration registerCasterCandidates(Object... candidates) {
                return CasterChain.this.registerCasterCandidates(candidates);
            }
        };
    }
}
