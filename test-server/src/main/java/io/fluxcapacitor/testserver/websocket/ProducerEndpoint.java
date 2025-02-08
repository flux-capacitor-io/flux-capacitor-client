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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.publishing.SetRetentionTime;
import io.fluxcapacitor.common.tracking.MessageStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@AllArgsConstructor
public class ProducerEndpoint extends WebsocketEndpoint {

    private final MessageStore store;

    @Handle
    CompletableFuture<Void> handle(Append request) {
        return store.append(request.getMessages().toArray(SerializedMessage[]::new));
    }

    @Handle
    void handle(SetRetentionTime request) {
        store.setRetentionTime(Optional.ofNullable(
                request.getRetentionTimeInSeconds()).map(Duration::ofSeconds).orElse(null));
    }
}
