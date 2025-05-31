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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import java.util.Optional;

/**
 * Strategy interface for validating message payloads and other objects prior to handler invocation.
 * <p>
 * Implementations of this interface are typically invoked by the {@link ValidatingInterceptor}, which is automatically
 * registered by the Flux Capacitor client. Validation occurs before the message is passed to the handler method.
 *
 * <p>
 * A {@code Validator} is responsible for detecting constraint violations and producing a {@link ValidationException} if
 * applicable. It supports validation groups to selectively apply rules.
 *
 * <h2>Usage</h2>
 * The validator may be used programmatically:
 *
 * <pre>{@code
 * validator.assertValid(new CreateUserCommand(...));
 * }</pre>
 *
 * <p>
 * But more commonly, it is used implicitly:
 * <ul>
 *     <li>When the {@link ValidatingInterceptor} is registered with the {@code HandlerFactory}</li>
 *     <li>Or when using dependency injection or framework-level validation support</li>
 * </ul>
 *
 * <h2>Custom Implementations</h2>
 * Custom validators can be created to support use cases like:
 * <ul>
 *     <li>JSR 380 / Bean Validation annotations (e.g., {@code @NotNull}, {@code @Size})</li>
 *     <li>Domain-specific validation rules</li>
 *     <li>Structural validation on incoming {@link io.fluxcapacitor.common.MessageType#WEBREQUEST web requests}</li>
 * </ul>
 *
 * <p>
 * This interface is designed to be functional and composable, enabling fluent use within client applications.
 *
 * @see ValidatingInterceptor
 * @see ValidationException
 */
@FunctionalInterface
public interface Validator {

    /**
     * Validates the given object and returns an optional {@link ValidationException} if the object is invalid.
     *
     * @param object the object to validate
     * @param groups optional validation groups to apply
     * @param <T>    the type of object being validated
     * @return an {@link Optional} containing the validation error if validation failed, or empty if valid
     */
    <T> Optional<ValidationException> checkValidity(T object, Class<?>... groups);

    /**
     * Validates the given object and throws a {@link ValidationException} if it is invalid.
     *
     * @param object the object to validate
     * @param groups optional validation groups to apply
     * @param <T>    the type of object being validated
     * @return the original object if valid
     * @throws ValidationException if the object is invalid
     */
    default <T> T assertValid(T object, Class<?>... groups) throws ValidationException {
        checkValidity(object, groups).ifPresent(e -> {
            throw e;
        });
        return object;
    }

    /**
     * Checks whether the given object is valid according to the defined validation rules.
     *
     * @param object the object to validate
     * @param groups optional validation groups to apply
     * @return {@code true} if the object is valid, {@code false} otherwise
     */
    default boolean isValid(Object object, Class<?>... groups) {
        return checkValidity(object, groups).isEmpty();
    }
}
