/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.common.api;

import lombok.ToString;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.util.Objects;
import java.util.function.Supplier;

@Value
@ToString(exclude = "value")
public class Data<T> implements SerializedObject<T, Data<T>> {
    Supplier<T> value;
    String type;
    int revision;
    String format;

    @ConstructorProperties({"value", "type", "revision", "format"})
    public Data(T value, String type, int revision, String format) {
        this.value = () -> value;
        this.type = type;
        this.revision = revision;
        this.format = format;
    }

    public Data(Supplier<T> value, String type, int revision, String format) {
        this.value = value;
        this.type = type;
        this.revision = revision;
        this.format = format;
    }

    public T getValue() {
        return value.get();
    }

    public String getFormat() {
        return format == null ? "json" : format;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Data<?> data = (Data<?>) o;
        return revision == data.revision &&
                Objects.deepEquals(getValue(), data.getValue()) &&
                Objects.equals(type, data.type)
                && Objects.equals(getFormat(), data.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), type, revision, format);
    }

    @Override
    public Data<T> data() {
        return this;
    }

    @Override
    public Data<T> withData(Data<T> data) {
        return data;
    }
}
