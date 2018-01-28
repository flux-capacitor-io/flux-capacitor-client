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

package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import lombok.Value;
import lombok.experimental.Wither;

@Value
public class SerializedSnapshot implements SerializedObject<byte[], SerializedSnapshot> {
    String aggregateId;
    long lastSequenceNumber;
    @Wither
    Data<byte[]> data;

    @Override
    public Data<byte[]> data() {
        return data;
    }
}
