/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class UpcasterChain<T> implements Upcaster<Data<T>> {

    public static <T> Upcaster<Data<byte[]>> create(Collection<?> upcasters, Converter<T> converter) {
        if (upcasters.isEmpty()) {
            return s -> s;
        }
        Upcaster<Data<T>> upcasterChain = create(upcasters, converter.getDataType());
        return stream -> {
            Stream<Data<T>> converted =
                    stream.map(d -> new Data<>(() -> converter.convert(d.getValue()), d.getType(), d.getRevision()));
            Stream<? extends Data<T>> upcasted = upcasterChain.upcast(converted);
            return upcasted.map(d -> new Data<>(memoize(() -> converter.convert(d.getValue())), d.getType(), d.getRevision()));
        };
    }

    public static <T> Upcaster<Data<T>> create(Collection<?> upcasters, Class<T> dataType) {
        if (upcasters.isEmpty()) {
            return s -> s;
        }
        return new UpcasterChain<>(UpcastInspector.inspect(upcasters, dataType));
    }

    private final Map<DataRevision, AnnotatedUpcaster<T>> upcasters;

    protected UpcasterChain(Collection<AnnotatedUpcaster<T>> upcasters) {
        this.upcasters =
                upcasters.stream().collect(toMap(u -> new DataRevision(u.getAnnotation()), identity(), (a, b) -> {
                    throw new SerializationException(
                            format("Failed to create upcaster chain. Methods '%s' and '%s' both apply to the same data revision.",
                                   a, b));
                }));
    }

    @Override
    public Stream<Data<T>> upcast(Stream<Data<T>> input) {
        return input.flatMap(i -> Optional.ofNullable(upcasters.get(new DataRevision(i)))
                .map(upcaster -> upcast(upcaster.upcast(i)))
                .orElse(Stream.of(i)));
    }

    @Value
    @AllArgsConstructor
    protected static class DataRevision {
        String type;
        int revision;

        protected DataRevision(Data<?> data) {
            this(data.getType(), data.getRevision());
        }

        protected DataRevision(Upcast annotation) {
            this(annotation.type(), annotation.revision());
        }
    }

}
