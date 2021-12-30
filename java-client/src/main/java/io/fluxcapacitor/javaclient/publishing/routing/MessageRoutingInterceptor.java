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

package io.fluxcapacitor.javaclient.publishing.routing;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;

@AllArgsConstructor
@Slf4j
public class MessageRoutingInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType messageType) {
        return message;
    }

    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage, Message m,
                                                     MessageType messageType) {
        if (m.getPayload() != null) {
            Class<?> payloadType = m.getPayload().getClass();
            RoutingKey typeAnnotation = payloadType.getAnnotation(RoutingKey.class);
            if (typeAnnotation != null) {
                String routingValue = m.getMetadata().get(typeAnnotation.metadataKey());
                if (routingValue == null) {
                    log.warn("Did not find metadata routingValue for {} for routing key of message {} (id {})",
                             typeAnnotation.metadataKey(), payloadType, m.getMessageId());
                } else {
                    serializedMessage.setSegment(ConsistentHashing.computeSegment(routingValue));
                    return serializedMessage;
                }
            }
        }
        SerializedMessage result =
                getAnnotatedPropertyValue(m.getPayload(), RoutingKey.class).map(Object::toString)
                        .map(ConsistentHashing::computeSegment).map(s -> {
                            serializedMessage.setSegment(s);
                            return serializedMessage;
                        }).orElse(serializedMessage);
        if (result.getSegment() == null && m instanceof Schedule) {
            result.setSegment(ConsistentHashing.computeSegment(((Schedule) m).getScheduleId()));
        }
        return result;
    }
}
