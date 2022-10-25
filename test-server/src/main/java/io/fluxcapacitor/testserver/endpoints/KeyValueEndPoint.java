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

import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.StoreValue;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class KeyValueEndPoint extends WebsocketEndpoint {

    private final KeyValueClient keyValueStore;

    @Handle
    public void handle(StoreValue storeValue) {
        keyValueStore.storeValue(storeValue.getValue().getKey(), storeValue.getValue().getValue(),
                                 storeValue.isIfAbsent(), storeValue.getGuarantee());
    }

    @Handle
    public GetValueResult handle(GetValue getValue) {
        return new GetValueResult(getValue.getRequestId(), keyValueStore.getValue(getValue.getKey()));
    }

    @Handle
    public void handle(DeleteValue deleteValue) {
        keyValueStore.deleteValue(deleteValue.getKey(), deleteValue.getGuarantee());
    }

    @Override
    public String toString() {
        return "KeyValueEndpoint";
    }
}
