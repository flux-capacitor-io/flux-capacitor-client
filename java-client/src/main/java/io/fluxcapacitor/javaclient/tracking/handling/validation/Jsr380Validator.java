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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import lombok.AllArgsConstructor;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import java.lang.annotation.ElementType;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.lang.annotation.ElementType.FIELD;

/**
 * This validator uses JSR 380 annotations. However, before attempting method and type validations it will first attempt
 * field validations. This strategy prevents issues if one of the validated methods depends on one or more validated
 * fields but those fields are invalid.
 */
@AllArgsConstructor
public class Jsr380Validator implements Validator {
    private final javax.validation.Validator fieldValidator;
    private final javax.validation.Validator otherValidator;

    public static Jsr380Validator createDefault() {
        return new Jsr380Validator(
                Validation.byDefaultProvider().configure().traversableResolver(new Resolver(type -> type == FIELD))
                        .buildValidatorFactory().getValidator(),
                Validation.byDefaultProvider().configure().traversableResolver(new Resolver(type -> type != FIELD))
                        .buildValidatorFactory().getValidator());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<ValidationException> checkValidity(T object) {
        Set<? extends ConstraintViolation<?>> violations = fieldValidator.validate(object);
        try {
            violations.addAll((Collection) otherValidator.validate(object));
        } catch (Exception e) {
            if (violations.isEmpty()) {
                throw e;
            }
        }
        return violations.isEmpty() ? Optional.empty() : Optional.of(new ValidationException(violations));
    }

    @AllArgsConstructor
    private static class Resolver implements TraversableResolver {
        private final Predicate<ElementType> typePredicate;

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                   Path pathToTraversableObject, ElementType elementType) {
            return typePredicate.test(elementType);
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                    Path pathToTraversableObject, ElementType elementType) {
            return typePredicate.test(elementType);
        }
    }
}
