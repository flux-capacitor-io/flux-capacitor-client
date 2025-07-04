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

package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.tracking.client.LocalTrackingClient;
import lombok.experimental.Delegate;

import java.time.Duration;

/**
 * A client implementation for managing scheduling operations using an in-memory schedule store.
 * This class extends {@link LocalTrackingClient} and implements the {@link SchedulingClient} interface.
 * It serves as a local, non-distributed scheduling client primarily useful for testing scenarios
 * or where an in-memory schedule store suffices.
 */
public class LocalSchedulingClient extends LocalTrackingClient implements SchedulingClient {

    public LocalSchedulingClient(Duration messageExpiration) {
        super(new InMemoryScheduleStore(messageExpiration), MessageType.SCHEDULE);
    }

    @Override
    @Delegate
    public InMemoryScheduleStore getMessageStore() {
        return (InMemoryScheduleStore) super.getMessageStore();
    }

    @Override
    public void close() {
        super.close();
    }
}
