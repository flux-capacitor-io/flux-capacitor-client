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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.GatewayException;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.WEBRESPONSE;

/**
 * Specialized implementation of the {@link ResultGateway} interface for sending web response messages.
 * <p>
 * This class is responsible for handling responses to web requests, dispatching the result message to the specified
 * target using a {@link GatewayClient}.
 * <p>
 * The dispatch process utilizes the {@link DispatchInterceptor} and {@link WebResponseMapper} to modify or monitor
 * {@link WebResponse web responses} before they are sent.
 *
 * @see ResultGateway
 */
@AllArgsConstructor
public class WebResponseGateway implements ResultGateway {

    public static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;

    private final GatewayClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final WebResponseMapper webResponseMapper;

    @Override
    public CompletableFuture<Void> respond(Object payload, Metadata metadata, String target, Integer requestId,
                                           Guarantee guarantee) {
        return respond(webResponseMapper.map(payload, metadata), target, requestId, guarantee);
    }

    private CompletableFuture<Void> respond(WebResponse rawResponse, String target, Integer requestId,
                                            Guarantee guarantee) {

        WebResponse response = (WebResponse) dispatchInterceptor.interceptDispatch(rawResponse, WEBRESPONSE, null);
        if (response == null) {
            return CompletableFuture.completedFuture(null);
        }
        Function<SerializedMessage, CompletableFuture<Void>> dispatcher = input -> {
            try {
                SerializedMessage serializedMessage =
                        dispatchInterceptor.modifySerializedMessage(input, response, WEBRESPONSE, null);
                if (serializedMessage != null) {
                    dispatchInterceptor.monitorDispatch(response, WEBRESPONSE, null);
                    serializedMessage.setTarget(target);
                    serializedMessage.setRequestId(requestId);
                    return client.append(guarantee, serializedMessage);
                }
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                throw new GatewayException("Failed to send response " + rawResponse.getPayloadClass(), e);
            }
        };
        return sendResponse(response, dispatcher);
    }

    protected CompletableFuture<Void> sendResponse(WebResponse response,
                                                   Function<SerializedMessage, CompletableFuture<Void>> dispatcher) {
        if (response.getPayload() instanceof InputStream inputStream) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            try (OutputStreamCapturer capturer = new OutputStreamCapturer(MAX_RESPONSE_SIZE, (chunk, last) -> {
                Metadata metadata = response.getMetadata().with(HasMetadata.FINAL_CHUNK, last.toString());
                SerializedMessage message = new SerializedMessage(new Data<>(chunk, null, 0),
                                                                  metadata, response.getMessageId(),
                                                                  response.getTimestamp().toEpochMilli());
                futures.add(dispatcher.apply(message));
            })) {
                try (inputStream) {
                    inputStream.transferTo(capturer);
                }
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        } else {
            return dispatcher.apply(response.serialize(serializer));
        }
    }
}
