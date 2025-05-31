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

package io.fluxcapacitor.javaclient.persisting.eventsourcing.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.tracking.client.LocalTrackingClient;
import lombok.experimental.Delegate;

import java.time.Duration;

/**
 * A client implementation for managing and storing events in an in-memory event store. The LocalEventStoreClient
 * extends the {@link LocalTrackingClient} to leverage its tracking and gateway functionality while applying it
 * specifically to event storage operations. It implements the {@link EventStoreClient} interface and serves as a
 * lightweight, local event store client, primarily for testing or non-distributed environments.
 */
public class LocalEventStoreClient extends LocalTrackingClient implements EventStoreClient {

    public LocalEventStoreClient(Duration messageExpiration) {
        super(new InMemoryEventStore(messageExpiration), MessageType.EVENT);
    }

    @Override
    @Delegate
    public InMemoryEventStore getMessageStore() {
        return (InMemoryEventStore) super.getMessageStore();
    }

    @Override
    public void close() {
        super.close();
    }
}
