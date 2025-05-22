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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway interface for sending result messages in response to a command or query.
 * <p>
 * The {@code ResultGateway} is used internally by Flux Capacitor to publish the result of a {@code request}, such as a
 * command or query, back to the requester. Each response must specify the target (usually a client ID) and a request ID
 * to correlate the result with the original request.
 *
 * <p>Responses can be sent as raw payloads or wrapped in a {@link Message}. Optionally, metadata and a
 * {@link Guarantee} can be supplied to control how delivery is handled.
 *
 * <p><strong>Note:</strong> The results of handler methods—such as
 * {@link HandleCommand} or {@link HandleQuery} methods—are automatically published to the {@code ResultGateway}. This
 * interface is typically only used directly when you want to manually complete a request at a later time, such as after
 * receiving a third-party callback or performing asynchronous processing unrelated to the original handler method.
 *
 * <p><strong>Example use case:</strong> Handling a payment gateway callback that matches a previously submitted
 * command request.
 *
 * @see Message
 * @see Guarantee
 */
public interface ResultGateway {

    /**
     * Sends a response message with default metadata and {@link Guarantee#NONE}.
     * <p>
     * If the provided response is a {@link Message}, its payload and metadata are extracted and used directly.
     * Otherwise, the response is wrapped as a new {@link Message}.
     *
     * @param response  the response object or {@link Message} to be sent
     * @param target    the recipient of the response (typically a client ID)
     * @param requestId the unique ID of the original request
     * @return a {@link CompletableFuture} that completes when the message is published (depending on the guarantee)
     */
    default CompletableFuture<Void> respond(Object response, String target, Integer requestId) {
        if (response instanceof Message) {
            return respond(((Message) response).getPayload(), ((Message) response).getMetadata(), target, requestId,
                           Guarantee.NONE);
        } else {
            return respond(response, Metadata.empty(), target, requestId, Guarantee.NONE);
        }
    }

    /**
     * Sends a response with the specified payload, metadata, target, request ID, and delivery guarantee.
     * <p>
     * This method gives full control over how and when the response is delivered.
     *
     * @param payload   the payload of the response
     * @param metadata  additional metadata to include
     * @param target    the intended recipient of the response
     * @param requestId the identifier of the original request
     * @param guarantee delivery guarantee (e.g., {@link Guarantee#SENT} or {@link Guarantee#STORED})
     * @return a {@link CompletableFuture} that completes when the response is dispatched (depending on the guarantee)
     */
    CompletableFuture<Void> respond(Object payload, Metadata metadata, String target, Integer requestId,
                                    Guarantee guarantee);
}
