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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.GatewayException;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.MessageType.WEBRESPONSE;

@AllArgsConstructor
public class WebResponseGateway implements ResultGateway {

    private final GatewayClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final WebResponseMapper webResponseMapper;

    @Override
    public CompletableFuture<Void> respond(Object payload, Metadata metadata, String target, Integer requestId, Guarantee guarantee) {
        return respond(webResponseMapper.map(payload, metadata), target, requestId, guarantee);
    }

    private CompletableFuture<Void> respond(WebResponse response, String target, Integer requestId, Guarantee guarantee) {
        try {
            Message message = dispatchInterceptor.interceptDispatch(response, WEBRESPONSE);
            SerializedMessage serializedMessage
                    = dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, WEBRESPONSE);
            serializedMessage.setTarget(target);
            serializedMessage.setRequestId(requestId);
            return client.send(guarantee, serializedMessage).asCompletableFuture();
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send response %s", response.getPayload()), e);
        }
    }
}
