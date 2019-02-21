package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.Getter;
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl;

import javax.validation.ConstraintViolation;
import java.beans.ConstructorProperties;
import java.lang.annotation.ElementType;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    protected static String format(ConstraintViolation<?> v) {
        if (((ConstraintDescriptorImpl) v.getConstraintDescriptor()).getElementType() == ElementType.METHOD) {
            return v.getMessage();
        }
        return String.format("%s %s", v.getPropertyPath(), v.getMessage());
    }

    protected static String format(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ValidationException::format).sorted().collect(joining(lineSeparator()));
    }
}
