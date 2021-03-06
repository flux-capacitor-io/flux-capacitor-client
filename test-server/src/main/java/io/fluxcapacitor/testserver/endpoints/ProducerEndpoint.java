/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ProducerEndpoint extends WebsocketEndpoint {

    private final InMemoryMessageStore store;

    @Handle
    public void handle(Append append) {
        store.send(append.getMessages().toArray(SerializedMessage[]::new));
    }
}
