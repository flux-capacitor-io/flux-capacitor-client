/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import lombok.AllArgsConstructor;
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.declaresField;
import static jakarta.validation.ElementKind.CONTAINER_ELEMENT;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.System.lineSeparator;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * This validator uses JSR 380 annotations. However, before attempting method and type validations it will first attempt
 * field validations. This strategy prevents issues if one of the validated methods depends on one or more validated
 * fields but those fields are invalid.
 */
@AllArgsConstructor
public class Jsr380JakartaValidator implements Validator {
    private final jakarta.validation.Validator fieldValidator;
    private final jakarta.validation.Validator defaultValidator;

    public static Jsr380JakartaValidator createDefault() {
        return new Jsr380JakartaValidator(
                Validation.byDefaultProvider().configure().traversableResolver(new FieldResolver())
                        .buildValidatorFactory().getValidator(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Optional<ValidationException> checkValidity(T object, Class<?>... groups) {
        Collection<? extends ConstraintViolation<?>> violations =
                new LinkedHashSet<>(fieldValidator.validate(object, groups));
        try {
            violations.addAll((Collection) defaultValidator.validate(object, groups));
        } catch (Exception e) {
            if (violations.isEmpty()) {
                throw e;
            }
        }
        return violations.isEmpty() ? Optional.empty() : Optional.of(newValidationException(violations));
    }

    protected ValidationException newValidationException(Collection<? extends ConstraintViolation<?>> violations) {
        return new ValidationException(format(violations, false).stream().collect(joining(lineSeparator())),
                                       format(violations, true));
    }

    protected SortedSet<String> format(Collection<? extends ConstraintViolation<?>> violations,
                                              boolean fullPath) {
        return violations.stream().map(v -> format(v, fullPath))
                .collect(toCollection(() -> new TreeSet<>(CASE_INSENSITIVE_ORDER)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static String format(ConstraintViolation<?> v, boolean fullPath) {
        //If the validator uses a custom message we just return the message, otherwise we add the property path
        try {
            ConstraintDescriptorImpl constraintDescriptor = (ConstraintDescriptorImpl) v.getConstraintDescriptor();
            Method method = constraintDescriptor.getAnnotationType().getDeclaredMethod("message");
            Object defaultMessage = method.getDefaultValue();
            if (!Objects.equals(defaultMessage, method.invoke(constraintDescriptor.getAnnotation()))) {
                return v.getMessage();
            }
        } catch (Exception ignored) {
        }
        return String.format("%s %s", getPropertyPath(v, fullPath), v.getMessage());
    }

    protected static String getPropertyPath(ConstraintViolation<?> v, boolean full) {
        if (full) {
            return v.getPropertyPath().toString();
        }
        List<Path.Node> path = StreamSupport.stream(v.getPropertyPath().spliterator(), false).collect(toList());
        path = path.stream().skip(Math.max(0, path.size() - 2)).collect(Collectors.toList());
        if (path.isEmpty()) {
            return v.getPropertyPath().toString();
        }
        if (path.size() == 2) {
            Path.Node a = path.get(0), b = path.get(1);
            return b.isInIterable() && b.getKind() != CONTAINER_ELEMENT ? b.getName() :
                    String.format("%s %s", a.getName(), b.getKind() == CONTAINER_ELEMENT ? "element" : b.getName());
        }
        return path.get(0).getName();
    }

    @AllArgsConstructor
    private static class FieldResolver implements TraversableResolver {
        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                   Path pathToTraversableObject, ElementType elementType) {
            if (elementType == TYPE_USE) {
                return declaresField(traversableObject.getClass(), traversableProperty.getName());
            }
            return elementType == FIELD;
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                    Path pathToTraversableObject, ElementType elementType) {
            return isReachable(traversableObject, traversableProperty, rootBeanType, pathToTraversableObject,
                               elementType);
        }
    }
}
