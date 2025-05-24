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

package io.fluxcapacitor.common.api.publishing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Command to publish messages to a specific log in Flux (e.g., commands, events, metrics, etc.).
 * <p>
 * The messages are written to the log associated with the given {@link #messageType}.
 * Each message is represented as a {@link SerializedMessage} and is appended to the log
 * in the order provided.
 * <p>
 * This operation is typically used by low-level clients (such as {@code GatewayClient})
 * that need full control over message serialization and targeting. High-level APIs usually
 * delegate to this command internally.
 *
 * @see MessageType
 * @see SerializedMessage
 */
@Value
public class Append extends Command {

    /**
     * The type of messages being appended (e.g., COMMAND, EVENT, etc.).
     */
    MessageType messageType;

    /**
     * The serialized messages to append to the log.
     */
    List<SerializedMessage> messages;

    /**
     * The delivery guarantee used when appending messages.
     */
    Guarantee guarantee;

    @JsonIgnore
    public int getSize() {
        return messages.size();
    }

    public Guarantee getGuarantee() {
        return ofNullable(guarantee).orElse(Guarantee.NONE);
    }

    @Override
    public String routingKey() {
        return messageType.name();
    }

    @Override
    public String toString() {
        return "Append of length " + messages.size();
    }

    @Override
    public Metric toMetric() {
        return new Metric(getMessageType(), getSize(), getGuarantee());
    }

    /**
     * Metric payload used for internal monitoring and logging.
     */
    @Value
    public static class Metric {
        MessageType messageType;
        int size;
        Guarantee guarantee;
    }
}
