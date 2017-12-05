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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.api.JsonType;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.nio.ByteBuffer;

public class JsonDecoder implements Decoder.Binary<JsonType> {

    private static final ObjectMapper defaultMapper = new ObjectMapper();

    private final ObjectMapper objectMapper;

    public JsonDecoder() {
        this(defaultMapper);
    }

    public JsonDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonType decode(ByteBuffer bytes) throws DecodeException {
        try {
            return objectMapper.readValue(bytes.array(), JsonType.class);
        } catch (IOException e) {
            throw new DecodeException(bytes, "Could not parse input string. Expected a Json message.", e);
        }
    }

    @Override
    public boolean willDecode(ByteBuffer bytes) {
        return true;
    }

    @Override
    public void init(EndpointConfig config) {

    }

    @Override
    public void destroy() {

    }
}
