/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;

@AllArgsConstructor
public class CorrelatingInterceptor implements DispatchInterceptor {
    private final Client client;
    private final String clientId;
    private final String correlationId;
    private final String traceId;
    private final String trigger;
    private final String triggerRoutingKey;

    public CorrelatingInterceptor(Client client) {
        this(client, "$clientId", "$correlationId", "$traceId", "$trigger", "$triggerRoutingKey");
    }

    @Override
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                  MessageType messageType) {
        return message -> {
            Map<String, String> result = new HashMap<>();
            result.put(clientId, client.id());
            DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
            if (currentMessage != null) {
                String correlationId = Optional.ofNullable(currentMessage.getSerializedObject().getIndex())
                        .map(Object::toString).orElse(currentMessage.getSerializedObject().getMessageId());
                result.put(this.correlationId, correlationId);
                result.put(traceId, currentMessage.getMetadata().getOrDefault(traceId, correlationId));
                result.put(trigger, currentMessage.getSerializedObject().getData().getType());
                if (currentMessage.isDeserialized()) {
                    getAnnotatedPropertyValue(currentMessage.getPayload(), RoutingKey.class).map(Object::toString)
                            .ifPresent(v -> result.put(triggerRoutingKey, v));
                }
            }
            return function.apply(message.withMetadata(message.getMetadata().with(result)));
        };
    }
}
