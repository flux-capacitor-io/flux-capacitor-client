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

package io.fluxcapacitor.common.api;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.With;

@lombok.Data
@AllArgsConstructor
public class SerializedMessage implements SerializedObject<byte[], SerializedMessage> {
    
    @With
    @NonNull
    private Data<byte[]> data;
    private Metadata metadata;
    @With
    private Integer segment;
    private Long index;
    private String source;
    private String target;
    private Integer requestId;
    private Long timestamp;
    private String messageId;

    public SerializedMessage(Data<byte[]> data, Metadata metadata, String messageId, Long timestamp) {
        this.data = data;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.messageId = messageId;
    }

    @Override
    public Data<byte[]> data() {
        return data;
    }
}
