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
import io.fluxcapacitor.common.reflection.ReflectionUtils;

/**
 * An abstract base class for converting data of type {@code I} to type {@code O}. This class implements the
 * {@link Converter} interface and provides a default behavior for the {@link #convert(Data)} method, while requiring
 * subclasses to define their own implementation for converting input values of type {@code I} to output values of type
 * {@code O}.
 *
 * @param <I> the type of the input data to be converted
 * @param <O> the type of the output data after the conversion
 */
public abstract class AbstractConverter<I, O> implements Converter<I, O> {

    @Override
    public Data<O> convert(Data<I> data) {
        return data.map(this::convert);
    }

    protected abstract O convert(I bytes);

    @Override
    public Class<O> getOutputType() {
        return ReflectionUtils.getTypeArgument(getClass().getGenericSuperclass(), 1);
    }

}
