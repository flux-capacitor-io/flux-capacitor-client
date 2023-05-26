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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class CasterChain<T> {

    private static final Comparator<AnnotatedCaster<?>> upcasterComparator =
            Comparator.<AnnotatedCaster<?>, Integer>comparing(u -> u.getParameters().revision())
                    .thenComparing(u -> u.getParameters().type());

    private static final Comparator<AnnotatedCaster<?>> downcasterComparator =
            Comparator.<AnnotatedCaster<?>, Integer>comparing(u -> u.getParameters().revision()).reversed()
                    .thenComparing(u -> u.getParameters().type());

    public static <T> Caster<SerializedObject<byte[], ?>> createUpcaster(Collection<?> casterCandidates,
                                                                         Converter<T> converter) {
        if (casterCandidates.isEmpty()) {
            return (s, desiredRevision) -> s;
        }
        Caster<ConvertingSerializedObject<T>> casterChain = create(casterCandidates, converter.getDataType(), false);
        return (stream, desiredRevision) -> {
            Stream<ConvertingSerializedObject<T>> converted =
                    stream.map(s -> new ConvertingSerializedObject<>(s, converter));
            Stream<ConvertingSerializedObject<T>> casted = casterChain.cast(converted);
            return casted.map(ConvertingSerializedObject::getResult);
        };
    }

    public static <T, S extends SerializedObject<T, S>> Caster<S> create(Collection<?> casterCandidates,
                                                                         Class<T> dataType, boolean down) {
        if (casterCandidates.isEmpty()) {
            return (s, desiredRevision) -> s;
        }
        List<AnnotatedCaster<T>> upcasterList =
                CastInspector.getCasters(down ? Downcast.class : Upcast.class, casterCandidates, dataType,
                                         down ? downcasterComparator : upcasterComparator);
        CasterChain<T> casterChain = new CasterChain<>(upcasterList);
        return casterChain::cast;
    }

    private final Map<DataRevision, AnnotatedCaster<T>> casters;

    protected CasterChain(Collection<AnnotatedCaster<T>> casters) {
        this.casters =
                casters.stream().collect(toMap(u -> new DataRevision(u.getParameters()), identity(), (a, b) -> {
                    throw new DeserializationException(
                            format("Failed to create caster chain. Methods '%s' and '%s' both apply to the same data revision.",
                                   a, b));
                }));
    }

    protected <S extends SerializedObject<T, S>> Stream<S> cast(Stream<S> input, Integer desiredRevision) {
        return input.flatMap(i -> Objects.equals(i.data().getRevision(), desiredRevision) ? Stream.of(i)
                : Optional.ofNullable(casters.get(new DataRevision(i.data())))
                .map(caster -> cast(caster.cast(i), desiredRevision))
                .orElseGet(() -> Stream.of(i)));
    }

    @Value
    @AllArgsConstructor
    protected static class DataRevision {
        String type;
        int revision;

        protected DataRevision(Data<?> data) {
            this(data.getType(), data.getRevision());
        }

        protected DataRevision(CastParameters annotation) {
            this(annotation.type(), annotation.revision());
        }
    }

    @AllArgsConstructor
    protected static class ConvertingSerializedObject<T> implements SerializedObject<T, ConvertingSerializedObject<T>> {

        private final SerializedObject<byte[], ?> source;
        private final Converter<T> converter;
        @With
        private Data<T> data;

        public ConvertingSerializedObject(SerializedObject<byte[], ?> source, Converter<T> converter) {
            this.source = source;
            this.converter = converter;
            this.data = converter.convert(source.data());
        }

        @Override
        public Data<T> data() {
            return data;
        }

        public SerializedObject<byte[], ?> getResult() {
            return source.withData(converter.convertBack(data));
        }
    }
}
