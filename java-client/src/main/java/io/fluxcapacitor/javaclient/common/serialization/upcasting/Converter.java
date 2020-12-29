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

package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;

import java.util.function.Supplier;

import static java.lang.String.format;

public interface Converter<T> {

    default Data<T> convert(Data<byte[]> data) {
        return new Data<>(() -> convert(data.getValue()), data.getType(), data.getRevision());
    }

    T convert(byte[] bytes);

    byte[] convertBack(T value);

    default Data<byte[]> convertBack(Data<T> data) {
        return new Data<>(() -> convertBack(data.getValue()), data.getType(), data.getRevision());
    }

    default boolean canApplyPatch(Class<?> type) {
        return false;
    }

    default Supplier<?> applyPatch(SerializedObject<T, ?> s, Supplier<?> o, Class<?> type) {
        throw new UnsupportedOperationException(format("Unable to apply type %s as patch using %s converter",
                type.getSimpleName(), getDataType().getSimpleName()));
    }

    Class<T> getDataType();

}
