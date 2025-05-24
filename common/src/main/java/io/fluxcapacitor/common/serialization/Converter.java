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

package io.fluxcapacitor.common.serialization;

import io.fluxcapacitor.common.api.Data;

/**
 * Interface for converting {@link Data} objects from one format or representation to another.
 * <p>
 * Converters are typically used to transform serialized data—e.g., for encoding changes, compression, encryption, or
 * changing the representation format (such as JSON to binary).
 *
 * @param <I> the input data format type
 * @param <O> the output data format type
 */
public interface Converter<I, O> {

    /**
     * Converts the given {@link Data} from input type {@code I} to output type {@code O}.
     *
     * @param data the input data
     * @return the converted data
     */
    Data<O> convert(Data<I> data);

    /**
     * Optionally converts the structure or format (e.g.: application/json) of the input data without modifying its core
     * content.
     * <p>
     * This method is used in scenarios where only a format change is required (e.g., wrapping metadata differently),
     * and may return the original input or a modified version depending on the implementation.
     *
     * @param data the input data
     * @return the format-converted data, or the original input (default implementation)
     */
    default Data<?> convertFormat(Data<I> data) {
        return data;
    }

    /**
     * @return the target output type this converter produces
     */
    Class<O> getOutputType();
}
