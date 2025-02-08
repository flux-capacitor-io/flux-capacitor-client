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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;

import java.util.Optional;

import static io.fluxcapacitor.common.ConsistentHashing.fallsInRange;

@Value
public class Tracker {
    public static final ThreadLocal<Tracker> current = new ThreadLocal<>();

    public static Optional<Tracker> current() {
        return Optional.ofNullable(current.get());
    }

    String name;
    String trackerId;
    MessageType messageType;
    String topic;
    ConsumerConfiguration configuration;

    @With
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    MessageBatch messageBatch;
    
    public boolean canHandle(DeserializingMessage message, String routingKey) {
        if (messageBatch == null || messageBatch.getPosition() == null) {
            return true;
        }
        int segment = ConsistentHashing.computeSegment(routingKey);
        return fallsInRange(segment, messageBatch.getSegment())
               && messageBatch.getPosition().isNewIndex(segment, message.getIndex());
    }
}
