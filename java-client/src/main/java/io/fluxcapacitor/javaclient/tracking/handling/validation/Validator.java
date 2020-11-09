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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import java.util.Optional;

@FunctionalInterface
public interface Validator {
    <T> Optional<ValidationException> checkValidity(T object, Class<?>... groups);


    default <T> T assertValid(T object, Class<?>... groups) throws ValidationException {
        checkValidity(object, groups).ifPresent(e -> {
            throw e;
        });
        return object;
    }

    default boolean isValid(Object object, Class<?>... groups) {
        return !checkValidity(object, groups).isPresent();
    }
}
