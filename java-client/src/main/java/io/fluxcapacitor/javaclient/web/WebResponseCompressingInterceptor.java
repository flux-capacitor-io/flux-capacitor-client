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
import java.util.List;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.isWebsocket;
import static java.util.Collections.emptyList;

/**
 * A {@link DispatchInterceptor} that applies GZIP compression to outgoing {@link WebResponse} messages based on request
 * headers and response size.
 * <p>
 * This interceptor checks whether the originating {@link WebRequest} includes an {@code Accept-Encoding} header with
 * {@code gzip}. If so, and if the response body exceeds a configurable minimum size threshold, it compresses the
 * response payload and adds the appropriate {@code Content-Encoding} metadata.
 *
 * <p>Compression is skipped in the following cases:
 * <ul>
 *     <li>The request was not a {@code WEBREQUEST}</li>
 *     <li>The request was made over a WebSocket</li>
 *     <li>The response already includes a {@code Content-Encoding} header</li>
 *     <li>The response is smaller than the configured minimum byte length</li>
 * </ul>
 *
 * <p>By default, the compression threshold is set to 2048 bytes.
 *
 * @see WebRequest
 * @see WebResponse
 * @see CompressionUtils
 */
public class WebResponseCompressingInterceptor implements DispatchInterceptor {

    /**
     * Minimum payload length (in bytes) for compression to be applied.
     */
    private final int minimumLength;

    /**
     * Creates a new interceptor with a default compression threshold of 2048 bytes.
     */
    public WebResponseCompressingInterceptor() {
        this(2048);
    }

    /**
     * Creates a new interceptor with a custom compression threshold.
     *
     * @param minimumLength minimum payload size (in bytes) for compression to apply
     */
    public WebResponseCompressingInterceptor(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    /**
     * Compresses the given response message using GZIP if the request supports compression and the response meets
     * compression criteria.
     *
     * @param response the serialized response message
     * @param message  the original message
     * @param type     the type of message being dispatched
     * @param topic    the dispatch topic
     * @return a potentially compressed {@link SerializedMessage}
     */
    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage response, Message message, MessageType type,
                                                     String topic) {
        return acceptCompression() && shouldCompress(response) ? compress(response) : response;
    }

    /**
     * Checks whether the current request accepts GZIP compression. This is determined by inspecting the
     * {@code Accept-Encoding} header.
     *
     * @return {@code true} if GZIP compression is accepted, {@code false} otherwise
     */
    protected boolean acceptCompression() {
        DeserializingMessage request = DeserializingMessage.getCurrent();
        if (request == null || request.getMessageType() != MessageType.WEBREQUEST) {
            return false;
        }
        String requestMethod = WebRequest.getMethod(request.getMetadata());
        if (requestMethod == null || isWebsocket(requestMethod)) {
            return false;
        }
        return WebRequest.getHeaders(request.getMetadata()).getOrDefault("Accept-Encoding", emptyList())
                .stream().flatMap(v -> Arrays.stream(v.split(",")).map(String::trim)).toList().contains("gzip");
    }

    /**
     * Determines whether the given serialized message should be compressed based on specific criteria.
     * <p>
     * A message will <strong>not</strong> be compressed if:
     * <ul>
     *   <li>The "X-Compression" header explicitly disables compression.</li>
     *   <li>The Content-Type of the message indicates that it is a media type (image, video, audio, or application/octet-stream).</li>
     *   <li>The "Content-Encoding" header is already present in the message.</li>
     *   <li>The HTTP status code of the response is not 200.</li>
     *   <li>The size of the message's payload is below the predefined threshold.</li>
     * </ul>
     */
    protected boolean shouldCompress(SerializedMessage response) {
        String compressionHint = WebUtils.getHeader(response.getMetadata(), "X-Compression")
                .orElse("").toLowerCase();
        if ("disabled".equals(compressionHint)) {
            return false;
        }
        String contentType = WebUtils.getHeader(response.getMetadata(), "Content-Type")
                .orElse("").toLowerCase();
        if (contentType.startsWith("image/")
            || contentType.startsWith("video/")
            || contentType.startsWith("audio/")
            || contentType.equals("application/octet-stream")) {
            return false;
        }
        if (WebResponse.getHeaders(response.getMetadata()).containsKey("Content-Encoding")) {
            return false;
        }
        if (WebResponse.getStatusCode(response.getMetadata()) != 200) {
            return false;
        }
        return response.getData().getValue().length >= minimumLength;
    }

    /**
     * Applies GZIP compression to the response payload and updates the metadata to indicate the
     * {@code Content-Encoding} used.
     *
     * @param response the message to compress
     * @return a new {@link SerializedMessage} with compressed payload and updated headers
     */
    protected SerializedMessage compress(SerializedMessage response) {
        var result = response.withData(
                response.getData().map(bytes -> CompressionUtils.compress(bytes, CompressionAlgorithm.GZIP)));
        var headers = WebUtils.getHeaders(result.getMetadata());
        headers.put("Content-Encoding", List.of("gzip"));
        headers.put("Content-Length", List.of(String.valueOf(result.getData().getValue().length)));
        result.setMetadata(result.getMetadata().with(WebResponse.headersKey, headers));
        return result;
    }

    /**
     * No-op for message interception. This interceptor only modifies serialized messages.
     *
     * @param message     the message to be dispatched
     * @param messageType the type of the message
     * @param topic       the dispatch topic
     * @return the unmodified message
     */
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        return message;
    }
}
