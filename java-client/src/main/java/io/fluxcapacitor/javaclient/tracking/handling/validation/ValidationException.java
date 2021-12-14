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

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import javax.validation.ConstraintViolation;
import javax.validation.Path;
import lombok.Getter;
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl;

import java.beans.ConstructorProperties;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.validation.ElementKind.CONTAINER_ELEMENT;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

@Getter
public class ValidationException extends FunctionalException {

    private final SortedSet<String> violations;

    public ValidationException(Collection<? extends ConstraintViolation<?>> violations) {
        super(format(violations, false).stream().collect(joining(lineSeparator())));
        this.violations = format(violations, true);
    }

    @ConstructorProperties({"message", "violations"})
    public ValidationException(String message, Set<String> violations) {
        super(message);
        this.violations = new TreeSet<>(violations);
    }

    protected static SortedSet<String> format(Collection<? extends ConstraintViolation<?>> violations,
                                              boolean fullPath) {
        return violations.stream().map(v -> format(v, fullPath))
                .collect(toCollection(() -> new TreeSet<>(CASE_INSENSITIVE_ORDER)));
    }

    @SuppressWarnings("unchecked")
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
}
