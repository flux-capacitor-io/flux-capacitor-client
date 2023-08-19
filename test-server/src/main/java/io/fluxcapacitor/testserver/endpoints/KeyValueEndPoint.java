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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.VoidResult;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.KeyValuePair;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsent;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesAndWait;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class KeyValueEndPoint extends WebsocketEndpoint {

    private final KeyValueClient keyValueStore;

    @Handle
    public void handle(StoreValues storeValues) {
        for (KeyValuePair value : storeValues.getValues()) {
            keyValueStore.putValue(value.getKey(), value.getValue(), Guarantee.NONE);
        }
    }

    @Handle
    public VoidResult handle(StoreValuesAndWait storeValues) throws Exception {
        for (KeyValuePair value : storeValues.getValues()) {
            keyValueStore.putValue(value.getKey(), value.getValue(), Guarantee.STORED).get();
        }
        return new VoidResult(storeValues.getRequestId());
    }

    @Handle
    public BooleanResult handle(StoreValueIfAbsent r) throws Exception {
        return new BooleanResult(r.getRequestId(),
                                 keyValueStore.putValueIfAbsent(r.getValue().getKey(), r.getValue().getValue()).get());
    }

    @Handle
    public GetValueResult handle(GetValue getValue) {
        return new GetValueResult(getValue.getRequestId(), keyValueStore.getValue(getValue.getKey()));
    }

    @Handle
    public void handle(DeleteValue deleteValue) {
        keyValueStore.deleteValue(deleteValue.getKey());
    }

    @Override
    public String toString() {
        return "KeyValueEndpoint";
    }
}
