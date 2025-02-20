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

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.serialization.Converter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class DefaultCasterChain<T> {

    private static final Comparator<AnnotatedCaster<?>> upcasterComparator =
            Comparator.<AnnotatedCaster<?>, Integer>comparing(u -> u.getParameters().revision())
                    .thenComparing(u -> u.getParameters().type());

    private static final Comparator<AnnotatedCaster<?>> downcasterComparator =
            Comparator.<AnnotatedCaster<?>, Integer>comparing(u -> u.getParameters().revision()).reversed()
                    .thenComparing(u -> u.getParameters().type());

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> Caster<SerializedObject<byte[], ?>, SerializedObject<?, ?>> createUpcaster(Collection<?> casterCandidates,
                                                                         Converter<byte[], T> converter) {
        Caster<ConvertingSerializedObject<byte[], T>, ? extends ConvertingSerializedObject> casterChain =
                create(casterCandidates, converter.getOutputType(), false);
        return (stream, desiredRevision) -> {
            Stream<ConvertingSerializedObject<byte[], T>> converted =
                    stream.map(s -> new ConvertingSerializedObject<>(s, converter));
            Stream<? extends ConvertingSerializedObject> casted = casterChain.cast(converted);
            return (Stream) casted.map(ConvertingSerializedObject::getResult);
        };
    }

    public static <T, S extends SerializedObject<T, S>> Caster<S, S> create(Collection<?> casterCandidates,
                                                                         Class<T> dataType, boolean down) {
        List<AnnotatedCaster<T>> upcasterList =
                CastInspector.getCasters(down ? Downcast.class : Upcast.class, casterCandidates, dataType,
                                         down ? downcasterComparator : upcasterComparator);
        DefaultCasterChain<T> casterChain = new DefaultCasterChain<>(upcasterList, down);
        return casterChain::cast;
    }

    private final Map<DataRevision, AnnotatedCaster<T>> casters;
    private final boolean down;

    protected DefaultCasterChain(Collection<AnnotatedCaster<T>> casters, boolean down) {
        this.casters =
                casters.stream().collect(toMap(u -> new DataRevision(u.getParameters()), identity(), (a, b) -> {
                    throw new DeserializationException(
                            format("Failed to create caster chain. Methods '%s' and '%s' both apply to the same data revision.",
                                   a, b));
                }));
        this.down = down;
    }

    protected <S extends SerializedObject<T, S>> Stream<S> cast(Stream<S> input, Integer desiredRevision) {
        return doCast(input, desiredRevision);
    }

    protected <S extends SerializedObject<T, S>> Stream<S> doCast(Stream<S> input, Integer desiredRevision) {
        return input.flatMap(i -> {
            boolean completed = desiredRevision != null
                                && (down ? i.getRevision() <= desiredRevision : i.getRevision() >= desiredRevision);
            return completed ? Stream.of(i)
                    : Optional.ofNullable(casters.get(new DataRevision(i.getType(), i.getRevision())))
                    .map(caster -> doCast(caster.cast(i), desiredRevision))
                    .orElseGet(() -> Stream.of(i));
        });
    }

    @Value
    @AllArgsConstructor
    protected static class DataRevision {
        String type;
        int revision;

        DataRevision(CastParameters annotation) {
            this(annotation.type(), annotation.revision());
        }
    }

    @AllArgsConstructor
    protected static class ConvertingSerializedObject<I, O>
            implements SerializedObject<O, ConvertingSerializedObject<I, O>>, HasSource<SerializedObject<I, ?>> {

        @Getter
        private final SerializedObject<I, ?> source;
        private final Converter<I, O> converter;
        @With
        private Data<O> data;

        public ConvertingSerializedObject(SerializedObject<I, ?> source, Converter<I, O> converter) {
            this.source = source;
            this.converter = converter;
        }

        @Override
        public Data<O> data() {
            if (data == null) {
                data = converter.convert(source.data());
            }
            return data;
        }

        @Override
        public String getType() {
            return data == null ? source.getType() : data.getType();
        }

        @Override
        public int getRevision() {
            return data == null ? source.getRevision() : data.getRevision();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public SerializedObject<?, ?> getResult() {
            if (data == null) {
                Data converted = converter.convertFormat(source.data());
                return converted == source.data() ? source : source.withData(converted);
            }
            return source.withData((Data) data);
        }
    }
}
