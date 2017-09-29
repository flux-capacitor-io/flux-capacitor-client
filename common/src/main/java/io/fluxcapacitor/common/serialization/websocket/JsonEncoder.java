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

package io.fluxcapacitor.common.serialization.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.serialization.CompressionUtils;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.nio.ByteBuffer;
import java.util.Objects;

import static java.lang.String.format;

public class JsonEncoder implements Encoder.Binary<Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ByteBuffer encode(Object object) throws EncodeException {
        try {
            return ByteBuffer.wrap(CompressionUtils.compress(objectMapper.writeValueAsBytes(object)));
        } catch (JsonProcessingException e) {
            throw new EncodeException(object, format("Could not convert %s to json", Objects.toString(object)), e);
        }
    }

    @Override
    public void init(EndpointConfig config) {

    }

    @Override
    public void destroy() {

    }
}
