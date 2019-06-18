package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.Getter;
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import java.beans.ConstructorProperties;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

@Getter
public class ValidationException extends FunctionalException {

    private final SortedSet<String> violations;

    public ValidationException(Set<? extends ConstraintViolation<?>> violations) {
        super(violations.stream().map(ValidationException::format).collect(Collectors.joining(lineSeparator())));
        this.violations = violations.stream().map(ValidationException::format).collect(toCollection(TreeSet::new));
    }

    @ConstructorProperties({"message", "violations"})
    public ValidationException(String message, Set<String> violations) {
        super(message);
        this.violations = new TreeSet<>(violations);
    }

    @SuppressWarnings("unchecked")
    protected static String format(ConstraintViolation<?> v) {
        //If the validator uses a custom message we just return the message, otherwise we add the property path
        try {
            ConstraintDescriptorImpl constraintDescriptor = (ConstraintDescriptorImpl) v.getConstraintDescriptor();
            Method method = constraintDescriptor.getAnnotationType().getDeclaredMethod("message");
            Object defaultMessage = method.getDefaultValue();
            if (!Objects.equals(defaultMessage, v.getMessage())) {
                return v.getMessage();
            }
        } catch (Exception ignored) {
        }
        return String.format("%s %s", StreamSupport.stream(v.getPropertyPath().spliterator(), false)
                .reduce((a, b) -> b).map(Path.Node::getName).orElse(v.getPropertyPath().toString()), v.getMessage());
    }

    protected static String format(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ValidationException::format).sorted().collect(joining(lineSeparator()));
    }
}
