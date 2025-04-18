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

package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.UnknownTypeStrategy;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;

import java.util.stream.Stream;

public class ProxySerializer implements Serializer {
    @Override
    public Data<byte[]> serialize(Object object, String format) {
        return new Data<>((byte[]) object, null, 0, format);
    }

    @Override
    public <I extends SerializedObject<byte[]>> Stream<DeserializingObject<byte[], I>> deserialize(
            Stream<I> stream, UnknownTypeStrategy unknownTypeStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> V convert(Object o, Class<V> aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> V clone(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Serializer registerTypeCaster(String s, String s1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String upcastType(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object downcast(Object o, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object downcast(Data<?> data, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T filterContent(T t, User user) {
        throw new UnsupportedOperationException();
    }
}
