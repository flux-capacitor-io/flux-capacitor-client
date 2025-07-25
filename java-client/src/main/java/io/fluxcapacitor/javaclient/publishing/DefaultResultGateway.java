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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.ResponseMapper;
import lombok.AllArgsConstructor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.MessageType.RESULT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;

/**
 * Default implementation of the {@link ResultGateway} interface for sending response messages.
 * <p>
 * This class is responsible for handling responses to commands, queries, dispatching the result message to the
 * specified target using a {@link GatewayClient}.
 * <p>
 * The dispatch process utilizes the {@link DispatchInterceptor} and {@link ResponseMapper} to modify or monitor
 * messages before they are sent.
 *
 * @see ResultGateway
 */
@AllArgsConstructor
public class DefaultResultGateway implements ResultGateway {

    private final GatewayClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final ResponseMapper responseMapper;

    @Override
    public CompletableFuture<Void> respond(Object payload, Metadata metadata, String target, Integer requestId,
                                           Guarantee guarantee) {
        try {
            SerializedMessage serializedMessage = interceptDispatch(payload, metadata);
            if (serializedMessage == null) {
                return CompletableFuture.completedFuture(null);
            }
            serializedMessage.setTarget(target);
            serializedMessage.setRequestId(requestId);
            return client.append(guarantee, serializedMessage);
        } catch (Exception e) {
            throw new GatewayException(String.format("Failed to send response %s",
                                                     payload != null && ifClass(payload) == null
                                                             ? payload.getClass() : Objects.toString(payload)), e);
        }
    }

    protected SerializedMessage interceptDispatch(Object payload, Metadata metadata) {
        Message message = dispatchInterceptor.interceptDispatch(responseMapper.map(payload, metadata), RESULT, null);
        SerializedMessage serializedMessage = message == null ? null
                : dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, RESULT, null);
        if (serializedMessage != null) {
            dispatchInterceptor.monitorDispatch(message, RESULT, null);
        }
        return serializedMessage;
    }
}
