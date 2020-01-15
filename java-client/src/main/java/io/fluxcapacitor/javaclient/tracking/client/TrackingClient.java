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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;
import lombok.SneakyThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TrackingClient extends AutoCloseable {

    @SneakyThrows
    default MessageBatch readAndWait(String consumer, String trackerId, TrackingConfiguration configuration) {
        return read(consumer, trackerId, configuration).get();
    }
    
    CompletableFuture<MessageBatch> read(String consumer, String trackerId, TrackingConfiguration trackingConfiguration);
    
    List<SerializedMessage> readFromIndex(long minIndex, int maxSize);

    Awaitable storePosition(String consumer, int[] segment, long lastIndex);

    Awaitable resetPosition(String consumer, long lastIndex);
    
    Awaitable disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch);

    @Override
    void close();
}
