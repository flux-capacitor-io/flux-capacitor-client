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

package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;

public interface HasMessage extends HasMetadata {
    Message toMessage();

    default <R> R getPayload() {
        return toMessage().getPayload();
    }

    default <R> R getPayloadAs(Class<R> type) {
        return JsonUtils.convertValue(getPayload(), type);
    }

    default Class<?> getPayloadClass() {
        Object payload = getPayload();
        return payload == null ? Void.class : payload.getClass();
    }

    default Optional<String> computeRoutingKey() {
        Message m = toMessage();
        String routingValue = null;
        if (m.getPayload() != null) {
            Class<?> payloadType = m.getPayload().getClass();
            RoutingKey typeAnnotation = payloadType.getAnnotation(RoutingKey.class);
            if (typeAnnotation != null) {
                routingValue = getMetadata().get(typeAnnotation.metadataKey());
                if (routingValue == null) {
                    LoggerFactory.getLogger(HasMessage.class).warn(
                            "Did not find metadata routingValue for {} for routing key of message {} (id {})",
                            typeAnnotation.metadataKey(), payloadType, m.getMessageId());
                } else {
                    return Optional.of(routingValue);
                }
            }
            routingValue = getAnnotatedPropertyValue(
                    m.getPayload(), RoutingKey.class).map(Object::toString).orElse(null);
        }
        if (routingValue == null && m instanceof Schedule) {
            routingValue = ((Schedule) m).getScheduleId();
        }
        return Optional.ofNullable(routingValue);
    }
}
