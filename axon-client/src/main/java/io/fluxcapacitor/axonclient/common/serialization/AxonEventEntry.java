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

package io.fluxcapacitor.axonclient.common.serialization;

import lombok.Value;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;

import java.time.Instant;

@Value
public class AxonEventEntry implements TrackedEventData<byte[]> {

    TrackingToken trackingToken;
    AxonMessage axonMessage;

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    public String getEventIdentifier() {
        return axonMessage.getId();
    }

    @Override
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(axonMessage.getTimestamp());
    }

    @Override
    public SerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<>(axonMessage.getPayload(), byte[].class, axonMessage.getType(), axonMessage.getRevision());
    }

    @Override
    public SerializedObject<byte[]> getMetaData() {
        return new SimpleSerializedObject<>(axonMessage.getMetadata(), byte[].class, MetaData.class.getName(), null);
    }
}
