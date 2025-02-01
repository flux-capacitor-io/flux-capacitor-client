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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.common.serialization.compression.CompressionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class WebResponseCompressingInterceptor implements DispatchInterceptor {
    private final int minimumLength;

    public WebResponseCompressingInterceptor() {
        this(2048);
    }

    public WebResponseCompressingInterceptor(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage response, Message message, MessageType type,
                                                     String topic) {
        return acceptCompression() && shouldCompress(response) ? compress(response) : response;
    }

    protected boolean acceptCompression() {
        DeserializingMessage request = DeserializingMessage.getCurrent();
        if (request == null || request.getMessageType() != MessageType.WEBREQUEST) {
            return false;
        }
        HttpRequestMethod requestMethod = WebRequest.getMethod(request.getMetadata());
        if (requestMethod == null || requestMethod.isWebsocket()) {
            return false;
        }
        return WebRequest.getHeaders(request.getMetadata()).getOrDefault("Accept-Encoding", emptyList())
                .stream().flatMap(v -> Arrays.stream(v.split(",")).map(String::trim)).toList().contains("gzip");
    }

    protected boolean shouldCompress(SerializedMessage response) {
        return !WebResponse.getHeaders(response.getMetadata()).containsKey("Content-Encoding")
               && response.getData().getValue().length >= minimumLength;
    }

    protected SerializedMessage compress(SerializedMessage serializedMessage) {
        var result = serializedMessage.withData(
                serializedMessage.getData().map(bytes -> CompressionUtils.compress(bytes, CompressionAlgorithm.GZIP)));
        @SuppressWarnings("unchecked")
        var headers = new LinkedHashMap<String, List<String>>(result.getMetadata().get("headers", Map.class));
        headers.put("Content-Encoding", List.of("gzip"));
        result.setMetadata(result.getMetadata().with("headers", headers));
        return result;
    }

    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        return message;
    }
}
