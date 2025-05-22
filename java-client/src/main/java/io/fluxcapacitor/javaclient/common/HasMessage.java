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

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

/**
 * Interface for objects that expose a backing {@link Message} instance.
 * <p>
 * This abstraction allows access to common message properties (payload, metadata, timestamp, ID, routing key) without
 * requiring the caller to know if they're working with a {@code Message}, {@code Schedule}, or a wrapped domain
 * object.
 * </p>
 *
 * <p>
 * Implementations must return a consistent {@link #toMessage()} representation and may override routing logic using
 * annotations such as {@link RoutingKey}.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Expose payload and metadata via delegation</li>
 *   <li>Enable routing key extraction via {@link #computeRoutingKey()}</li>
 *   <li>Support typed deserialization using {@link #getPayloadAs(Type)}</li>
 * </ul>
 *
 * @see Message
 * @see Schedule
 * @see RoutingKey
 * @see HasMetadata
 */
public interface HasMessage extends HasMetadata {
    BiFunction<Class<?>, String, AtomicBoolean> warnedAboutMissingProperty =
            memoize((type, property) -> new AtomicBoolean());

    /**
     * Returns the underlying {@link Message} representation of this object.
     *
     * @return the {@code Message} backing this instance
     */
    Message toMessage();

    /**
     * Retrieves the message payload, deserializing if necessary, cast to the expected type.
     * <p>
     * By default, this delegates to {@code toMessage().getPayload()}.
     *
     * @param <R> the expected payload type
     * @return the deserialized payload
     */
    default <R> R getPayload() {
        return toMessage().getPayload();
    }

    /**
     * Retrieves the message payload, deserializing if necessary and optionally converted to the given type.
     * <p>
     * By default, this performs a conversion of the payload using {@link JsonUtils}.
     *
     * @param <R> the expected payload type
     * @return the payload converted to the given type
     */
    default <R> R getPayloadAs(Type type) {
        return JsonUtils.convertValue(getPayload(), type);
    }

    /**
     * Returns the runtime class of the payload object, or {@code Void.class} if the payload is {@code null}.
     *
     * @return the payload's class
     */
    default Class<?> getPayloadClass() {
        Object payload = getPayload();
        return payload == null ? Void.class : payload.getClass();
    }

    /**
     * Returns the unique ID of the underlying message.
     *
     * @return the message ID
     */
    default String getMessageId() {
        return toMessage().getMessageId();
    }

    /**
     * Returns the timestamp at which the message was created or published.
     *
     * @return the message timestamp
     */
    default Instant getTimestamp() {
        return toMessage().getTimestamp();
    }

    /**
     * Computes the default routing key for this message.
     * <p>
     * The key is extracted based on the {@link RoutingKey} annotation on the payload type or one of its properties. If
     * none is found, and the message is a {@link Schedule}, the schedule ID is used instead.
     *
     * @return the resolved routing key, if available
     */
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

    /**
     * Attempts to resolve the routing key from the given property name.
     * <p>
     * This checks metadata first, then the payload itself. Logs a warning the first time a property is missing for a
     * given payload class.
     *
     * @param propertyName the name of the field, method, or metadata key to use
     * @return the routing key if found
     */
    default Optional<String> getRoutingKey(String propertyName) {
        return getRoutingKey(propertyName, true);
    }

    /**
     * Attempts to resolve the routing key from the specified property name.
     * <p>
     * Optionally logs a warning if the key is missing, but only once per class-property combination.
     *
     * @param propertyName  the property to use for routing
     * @param warnIfMissing whether to log a warning on first failure
     * @return the routing key if resolved
     */
    default Optional<String> getRoutingKey(String propertyName, boolean warnIfMissing) {
        String result = getMetadata().get(propertyName);
        if (result == null) {
            result = readProperty(propertyName, getPayload())
                    .map(Object::toString).orElse(null);
        }
        if (result == null && warnIfMissing && warnedAboutMissingProperty.apply(getPayloadClass(), propertyName)
                .compareAndSet(false, true)) {
            LoggerFactory.getLogger(HasMessage.class).warn(
                    "Did not find property (field, method, or metadata key) '{}' for routing key on message {} (id {})",
                    propertyName, getPayloadClass(), toMessage().getMessageId());
        }
        return Optional.ofNullable(result);
    }
}
