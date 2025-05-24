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

package io.fluxcapacitor.common.api.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.tracking.MessageStore;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.beans.ConstructorProperties;

/**
 * A request to claim a message segment for consumption in client-controlled tracking mode.
 * <p>
 * This is a specialization of {@link Read} that registers the tracker and reserves a segment range for local message
 * consumption. It does not fetch messages ({@code maxSize = 0}). Instead, it enables the client to handle message
 * delivery independently, for example:
 *
 * <h2>Typical usage</h2>
 * This is used when:
 * <ul>
 *     <li>The tracker wants to fetch messages from a client-side message cache</li>
 *     <li>The tracker wants to fetch messages manually from a {@link MessageStore} or other source</li>
 *     <li>The tracker handles <em>external messages</em> (e.g. updates from an external API) and wants to filter
 *     those based on its assigned segment range</li>
 * </ul>
 *
 * <h2>External source integration</h2>
 * In distributed systems where multiple application instances are polling external sources,
 * using {@code ClaimSegment} allows each instance to claim a distinct range of segments.
 * This enables segment-based filtering of externally received updates, preventing duplicate
 * processing across instances and offering a load-balanced coordination mechanism.
 *
 * <p>
 * This approach helps replicate Flux’s load balancing capabilities for external streams,
 * even when the external system does not support partitioning or consumer groups.
 *
 * @see Read
 * @see MessageStore
 * @see io.fluxcapacitor.common.api.tracking.Position
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ClaimSegment extends Read {
    @ConstructorProperties({"messageType", "consumer", "trackerId", "maxTimeout", "clientControlledIndex", "typeFilter", "filterMessageTarget", "lastIndex", "purgeTimeout"})
    public ClaimSegment(MessageType messageType, String consumer, String trackerId, long maxTimeout, boolean clientControlledIndex, String typeFilter,
                        boolean filterMessageTarget, Long lastIndex, Long purgeTimeout) {
        super(messageType, consumer, trackerId, 0, maxTimeout, typeFilter, filterMessageTarget, false,
              false, clientControlledIndex, lastIndex, purgeTimeout);
    }

    public ClaimSegment(Read read) {
        this(read.getMessageType(), read.getConsumer(), read.getTrackerId(), read.getMaxTimeout(), read.isClientControlledIndex(),
             read.getTypeFilter(), read.isFilterMessageTarget(), read.getLastIndex(), read.getPurgeTimeout());
    }
}
