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

/**
 * This validator uses JSR 380 annotations like {@link Jsr380Validator}. However, before attempting method validation it
 * will first attempt field validation.
 * <p>
 * Using this validator prevents issues if one of the validated methods depends on one or more validated fields but
 * those fields are null. Even if those fields all have @NotNull or @Blank annotations the method validations would give
 * rise to a NullPointerException and the entire validation would break.
 * <p>
 * If {@link Jsr380CrossPropertyValidator} is used field violations are checked before method violations. Any violations
 * will be combined, except if there are field annotations but the method check gives rise to an Exception. In that case
 * only field violations are returned.
 */
@AllArgsConstructor
public class Jsr380CrossPropertyValidator implements Validator {
    private final javax.validation.Validator fieldValidator;
    private final javax.validation.Validator defaultValidator;

    public static Jsr380CrossPropertyValidator createDefault() {
        return new Jsr380CrossPropertyValidator(
                Validation.byDefaultProvider().configure().traversableResolver(new FieldResolver())
                        .buildValidatorFactory().getValidator(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<ValidationException> checkValidity(T object) {
        Set<? extends ConstraintViolation<?>> violations = fieldValidator.validate(object);
        try {
            violations.addAll((Collection) defaultValidator.validate(object));
        } catch (Exception e) {
            if (violations.isEmpty()) {
                throw e;
            }
        }
        return violations.isEmpty() ? Optional.empty() : Optional.of(new ValidationException(violations));
    }

    private static class FieldResolver implements TraversableResolver {

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                   Path pathToTraversableObject, ElementType elementType) {
            return elementType == ElementType.FIELD;
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                    Path pathToTraversableObject, ElementType elementType) {
            return elementType == ElementType.FIELD;
        }
    }
}
