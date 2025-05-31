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

/**
 * Functional interface representing a transformation from one stream of values to another, possibly adjusting for
 * version compatibility or format changes.
 *
 * <p>This interface is typically used to implement:
 * <ul>
 *   <li><b>Upcasting</b>: Transforming older versions of serialized data to newer schema versions.</li>
 *   <li><b>Downcasting</b>: Reducing an object’s structure to an earlier schema version.</li>
 * </ul>
 *
 * <p>The {@link #cast(Stream, Integer)} method may inspect the desired revision and apply transformation logic
 * accordingly.
 *
 * <p>Casters are often discovered and applied as part of a {@link CasterChain}, which enables chaining and runtime
 * composition of multiple casting transformations.
 *
 * @param <I> the input type before casting
 * @param <O> the output type after casting
 */
@FunctionalInterface
public interface Caster<I, O> {

    /**
     * Casts the given stream of input values to the output format using the default transformation rules.
     * This is equivalent to calling {@code cast(input, null)}.
     *
     * @param input the input stream of values
     * @return a stream of transformed output values
     */
    default Stream<? extends O> cast(Stream<? extends I> input) {
        return cast(input, null);
    }

    /**
     * Casts the given stream of input values to the output format, optionally considering a target revision.
     *
     * @param input            the input stream of values
     * @param desiredRevision  the target revision number (nullable)
     * @return a stream of transformed output values
     */
    Stream<? extends O> cast(Stream<? extends I> input, Integer desiredRevision);

    /**
     * Creates a new {@code Caster} that applies preprocessing and postprocessing steps before and after the core cast.
     *
     * @param before a function to transform input from type {@code BEFORE} to {@code I}
     * @param after  a function to transform output from {@code O} to {@code AFTER}
     * @param <BEFORE> the new input type
     * @param <AFTER> the new output type
     * @return a composed {@code Caster} with adapted input/output types
     */
    default <BEFORE, AFTER> Caster<BEFORE, AFTER> intercept(Function<BEFORE, ? extends I> before,
                                                            Function<? super O, AFTER> after) {
        return (inputStream, rev) -> cast(inputStream.map(before), rev).map(after);
    }

}
