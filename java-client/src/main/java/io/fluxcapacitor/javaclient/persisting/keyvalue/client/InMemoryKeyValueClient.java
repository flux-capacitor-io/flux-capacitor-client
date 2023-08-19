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

package io.fluxcapacitor.javaclient.persisting.keyvalue.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKeyValueClient implements KeyValueClient {

    private final Map<String, Data<byte[]>> values;

    public InMemoryKeyValueClient() {
        this(new ConcurrentHashMap<>());
    }

    protected InMemoryKeyValueClient(Map<String, Data<byte[]>> map) {
        values = map;
    }

    @Override
    public CompletableFuture<Void> putValue(String key, Data<byte[]> value, Guarantee guarantee) {
        values.put(key, value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> putValueIfAbsent(String key, Data<byte[]> value) {
        return CompletableFuture.completedFuture(values.putIfAbsent(key, value) == null);
    }

    @Override
    public Data<byte[]> getValue(String key) {
        return values.get(key);
    }

    @Override
    public CompletableFuture<Void> deleteValue(String key, Guarantee guarantee) {
        values.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        //no op
    }
}
