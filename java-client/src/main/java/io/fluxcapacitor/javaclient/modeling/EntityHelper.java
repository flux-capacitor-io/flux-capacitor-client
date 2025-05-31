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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Strategy interface for handling domain entity operations such as applying updates,
 * validating state transitions, and intercepting updates.
 * <p>
 * Implementations of this interface coordinate the invocation of methods annotated with
 * {@link InterceptApply}, {@link AssertLegal}, and {@link Apply}.
 * <p>
 * These operations allow Flux Capacitor entities to transform messages before applying them, enforce invariants,
 * and apply updates.
 */
public interface EntityHelper {

    /**
     * Intercepts the given value before it is applied to an entity. This may result in transformation or
     * expansion of the value to one or more derived messages.
     *
     * @param value the value to be intercepted, typically the payload of a message
     * @param entity the entity receiving the value
     * @return a stream of intercepted values or messages to be applied
     */
    Stream<?> intercept(Object value, Entity<?> entity);

    /**
     * Returns an invoker that can apply the given event to the provided entity.
     *
     * @param message the message to apply
     * @param entity the entity to which the message should be applied
     * @return a handler invoker if an applicable handler method is found
     */
    default Optional<HandlerInvoker> applyInvoker(DeserializingMessage message, Entity<?> entity) {
        return applyInvoker(message, entity, false);
    }

    /**
     * Returns an invoker for applying the event to the entity, optionally checking nested entities.
     *
     * @param message the event to apply
     * @param entity the root or intermediate entity
     * @param searchChildren whether to search nested child entities for applicable handlers
     * @return a handler invoker if a suitable method is located
     */
    Optional<HandlerInvoker> applyInvoker(DeserializingMessage message, Entity<?> entity, boolean searchChildren);

    /**
     * Validates whether the given value results in a legal state transition for the specified entity.
     * Throws an exception if the result would be illegal.
     *
     * @param value the value to validate
     * @param entity the current entity state
     * @param <E> the type of exception that may be thrown
     * @throws E if the value is deemed illegal
     */
    <E extends Exception> void assertLegal(Object value, Entity<?> entity) throws E;

    /**
     * Checks if the given value would be considered legal for the specified entity.
     * Does not throw but instead returns an exception if illegal.
     *
     * @param value the value to validate
     * @param entity the entity context
     * @param <E> the type of exception
     * @return an exception if illegal, or an empty Optional if legal
     */
    <E extends Exception> Optional<E> checkLegality(Object value, Entity<?> entity);

    /**
     * Returns whether the given value is considered legal for the specified entity.
     *
     * @param value the value to check
     * @param entity the entity context
     * @return true if legal, false otherwise
     */
    boolean isLegal(Object value, Entity<?> entity);
}
