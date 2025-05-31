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

/**
 * A composite {@link Caster} that supports registration of multiple casting strategies.
 *
 * <p>This interface allows dynamic addition of {@code Caster} instances, which may be applied in sequence or based on
 * type matching logic. {@code CasterChain}s are used in serialization and deserialization pipelines to handle
 * transformations like upcasting, downcasting, or schema evolution.
 *
 * <p>It extends {@link Caster} and adds registration capabilities.
 *
 * @param <I> the input type
 * @param <O> the output type
 */
public interface CasterChain<I, O> extends Caster<I, O> {

    /**
     * Registers one or more objects that may contain casting logic (e.g. annotated methods or implementations).
     * These candidates are inspected and included into the chain if applicable.
     *
     * @param candidates one or more caster providers
     * @return a {@link Registration} that can be used to remove the registered casters
     */
    Registration registerCasterCandidates(Object... candidates);

    /**
     * Composes this caster chain with input and output interceptors. Allows pre- and post-processing transformations
     * around the casting logic.
     *
     * @param before a function to convert from {@code BEFORE} to {@code I}
     * @param after  a function to convert from {@code O} to {@code AFTER}
     * @param <BEFORE> the new input type
     * @param <AFTER> the new output type
     * @return a new {@code CasterChain} with adapted input/output types
     */
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
