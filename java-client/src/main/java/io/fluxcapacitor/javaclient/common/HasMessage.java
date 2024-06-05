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

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

public interface HasMessage extends HasMetadata {
    BiFunction<Class<?>, String, AtomicBoolean> warnedAboutMissingProperty = memoize((type, property) -> new AtomicBoolean());

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

    default String getMessageId() {
        return toMessage().getMessageId();
    }

    default Instant getTimestamp() {
        return toMessage().getTimestamp();
    }

    default Optional<String> computeRoutingKey() {
        Message m = toMessage();
        String routingValue = null;
        Class<?> payloadType = m.getPayloadClass();
        RoutingKey typeAnnotation = Optional.ofNullable(payloadType.getAnnotation(RoutingKey.class))
                .filter(a -> !a.value().isBlank()).orElse(null);
        if (typeAnnotation != null) {
            return getRoutingKey(typeAnnotation.value());
        }
        if (m.getPayload() != null) {
            routingValue = getAnnotatedPropertyValue(
                    m.getPayload(), RoutingKey.class).map(Object::toString).orElse(null);
        }
        if (routingValue == null && m instanceof Schedule) {
            routingValue = ((Schedule) m).getScheduleId();
        }
        return Optional.ofNullable(routingValue);
    }

    default Optional<String> getRoutingKey(String propertyName) {
        String result = getMetadata().get(propertyName);
        if (result == null) {
            result = readProperty(propertyName, getPayload())
                    .map(Object::toString).orElse(null);
        }
        if (result == null && warnedAboutMissingProperty.apply(getPayloadClass(), propertyName)
                .compareAndSet(false, true)) {
            LoggerFactory.getLogger(HasMessage.class).warn(
                    "Did not find property (field, method, or metadata key) '{}' for routing key on message {} (id {})",
                    propertyName, getPayloadClass(), toMessage().getMessageId());
        }
        return Optional.ofNullable(result);
    }
}
