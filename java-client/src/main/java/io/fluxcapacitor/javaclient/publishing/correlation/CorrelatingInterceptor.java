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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AllArgsConstructor;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;

/**
 * A {@link DispatchInterceptor} that enriches outgoing messages with correlation metadata,
 * enabling full traceability across message flows within Flux Capacitor.
 *
 * <p>This interceptor ensures that dispatched messages inherit and extend the correlation context
 * from the currently handled message and runtime environment.
 * It collects metadata such as:
 * <ul>
 *   <li>Application ID and client identifiers</li>
 *   <li>Consumer/tracker information (if running inside a {@link io.fluxcapacitor.javaclient.tracking.Tracker})</li>
 *   <li>Invocation ID (if within a tracked {@link io.fluxcapacitor.javaclient.tracking.handling.Invocation})</li>
 *   <li>Correlation ID and trace ID</li>
 *   <li>Trigger type and message type</li>
 * </ul>
 *
 * <p>Specifically, if the current message being handled is a request (e.g., a command or query)
 * and the outgoing message is an event, the outgoing event will inherit the metadata of the incoming request.
 * This is important for tying emitted events to the initiating request in logs, audits, or UIs.
 *
 * <p>In all cases, the interceptor adds the current correlation data retrieved from:
 * {@link FluxCapacitor#currentCorrelationData()}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Event event = new MyEvent(...);
 * eventGateway.publish(event);
 * }</pre>
 * The published event will automatically contain metadata such as:
 * <ul>
 *   <li>{@code $correlationId}</li>
 *   <li>{@code $traceId}</li>
 *   <li>{@code $trigger}</li>
 *   <li>{@code $clientId}, {@code $clientName}</li>
 *   <li>{@code $tracker}, {@code $consumer}</li>
 * </ul>
 *
 * @see FluxCapacitor#currentCorrelationData()
 * @see DefaultCorrelationDataProvider
 * @see DispatchInterceptor
 */
@AllArgsConstructor
public class CorrelatingInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        Metadata metadata = message.getMetadata();
        if (messageType == MessageType.EVENT) {
            DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
            if (currentMessage != null && currentMessage.getMessageType().isRequest()) {
                metadata = currentMessage.getMetadata().with(metadata);
            }
        }
        return message.withMetadata(metadata.with(currentCorrelationData()));
    }
}
