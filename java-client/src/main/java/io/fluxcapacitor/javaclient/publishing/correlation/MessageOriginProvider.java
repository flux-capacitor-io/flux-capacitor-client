/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@EqualsAndHashCode
public class MessageOriginProvider implements CorrelationDataProvider {
    private final Client client;
    private final String clientId;
    private final String correlationId;
    private final String traceId;
    private final String trigger;

    public MessageOriginProvider(Client client) {
        this(client, "$clientId", "$correlationId", "$traceId", "$trigger");
    }

    @Override
    public Map<String, String> fromMessage(DeserializingMessage message) {
        Map<String, String> result = new HashMap<>();
        Long index = message.getSerializedObject().getIndex();
        if (index != null) {
            String correlationId = index.toString();
            result.put(this.correlationId, correlationId);
            result.put(traceId, message.getMetadata().getOrDefault(traceId, correlationId));
        }
        result.put(clientId, client.id());
        result.put(trigger, message.getSerializedObject().getData().getType());
        return result;
    }
}
