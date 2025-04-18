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
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.serialization.Converter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class DefaultCasterChain<T, S extends SerializedObject<T>> implements CasterChain<S, S> {

    public static <BEFORE, INTERNAL> CasterChain<SerializedObject<BEFORE>, SerializedObject<?>> createUpcaster(
            Collection<?> casterCandidates, Converter<BEFORE, INTERNAL> converter) {
        return create(casterCandidates, converter, false);
    }

    public static <T, S extends SerializedObject<T>> CasterChain<S, S> createUpcaster(
            Collection<?> casterCandidates, Class<T> dataType) {
        return create(casterCandidates, dataType, false);
    }

    public static <T, S extends SerializedObject<T>> CasterChain<S, S> createDowncaster(
            Collection<?> casterCandidates, Class<T> dataType) {
        return create(casterCandidates, dataType, true);
    }

    protected static <BEFORE, INTERNAL> CasterChain<SerializedObject<BEFORE>, SerializedObject<?>> create(
            Collection<?> casterCandidates, Converter<BEFORE, INTERNAL> converter, boolean down) {
        return create(casterCandidates, converter.getOutputType(), down)
                .intercept(i -> new ConvertingSerializedObject<>(i, converter),
                           o -> ((ConvertingSerializedObject<?, ?>) o).getResult());
    }

    protected static <T, S extends SerializedObject<T>> CasterChain<S, S> create(
            Collection<?> casterCandidates, Class<T> dataType, boolean down) {
        return new DefaultCasterChain<>(casterCandidates, dataType, down);
    }

    private final Map<DataRevision, AnnotatedCaster<T>> casters;
    private final boolean down;
    private final Class<T> dataType;

    protected DefaultCasterChain(Collection<?> casterCandidates, Class<T> dataType, boolean down) {
        this.casters = CastInspector.getCasters(down ? Downcast.class : Upcast.class, casterCandidates, dataType)
                .stream().collect(toMap(u -> new DataRevision(u.getParameters()), identity(), (a, b) -> {
                    throw new DeserializationException(format(
                            "Failed to create CasterChain. Methods '%s' and '%s' both apply to the same data revision.",
                            a, b));
                }, HashMap::new));
        this.down = down;
        this.dataType = dataType;
    }

    @Override
    public Registration registerCasterCandidates(Object... candidates) {
        return CastInspector.getCasters(down ? Downcast.class : Upcast.class, Arrays.asList(candidates), dataType)
                .stream().<Registration>map(c -> {
                    DataRevision dataRevision = new DataRevision(c.getParameters());
                    if (casters.putIfAbsent(dataRevision, c) != null) {
                        throw new DeserializationException(format(
                                "Failed to register candidate. A caster for %s already exists.", dataRevision));
                    }
                    return () -> casters.remove(dataRevision);
                }).reduce(Registration::merge).orElseGet(Registration::noOp);
    }

    @Override
    public Stream<S> cast(Stream<? extends S> input, Integer desiredRevision) {
        return input.flatMap(i -> {
            boolean completed = desiredRevision != null
                                && (down ? i.getRevision() <= desiredRevision : i.getRevision() >= desiredRevision);
            return completed ? Stream.of(i)
                    : Optional.ofNullable(casters.get(new DataRevision(i.getType(), i.getRevision())))
                    .map(caster -> cast(caster.cast(i), desiredRevision))
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
            implements SerializedObject<O>, HasSource<SerializedObject<I>> {

        @Getter
        private final SerializedObject<I> source;
        private final Converter<I, O> converter;
        @With
        private Data<O> data;

        public ConvertingSerializedObject(SerializedObject<I> source, Converter<I, O> converter) {
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
        public SerializedObject<?> getResult() {
            if (data == null) {
                Data converted = converter.convertFormat(source.data());
                return converted == source.data() ? source : source.withData(converted);
            }
            return source.withData((Data) data);
        }
    }
}
