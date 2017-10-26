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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.Data;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public interface Serializer {

    Data<byte[]> serialize(Object object);

    @SuppressWarnings("unchecked")
    default <T> T deserialize(Data<byte[]> data) {
        List list = deserialize(Stream.of(data)).collect(toList());
        if (list.size() != 1) {
            throw new IllegalStateException(
                    String.format("Invalid deserialization result for a '%s'. Expected a single object but got %s",
                                  data, list));
        }
        return (T) list.get(0);
    }

    Stream<Object> deserialize(Stream<Data<byte[]>> dataStream);

}
