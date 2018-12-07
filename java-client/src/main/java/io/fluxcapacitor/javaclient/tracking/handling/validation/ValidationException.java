package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.Getter;

import javax.validation.ConstraintViolation;
import java.beans.ConstructorProperties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

@Getter
public class ValidationException extends FunctionalException {

    private final SortedSet<String> violations;

    public ValidationException(Set<? extends ConstraintViolation<?>> violations) {
        super(String.format("One or more constraints were violated:%s%s", lineSeparator(), format(violations)));
        this.violations = violations.stream().map(ValidationException::format).collect(toCollection(TreeSet::new));
    }

    @ConstructorProperties({"message", "violations"})
    public ValidationException(String message, Set<String> violations) {
        super(message);
        this.violations = new TreeSet<>(violations);
    }

    protected static String format(ConstraintViolation<?> v) {
        return String.format("property %s in class %s %s", v.getPropertyPath(), v.getRootBeanClass().getSimpleName(),
                             v.getMessage());
    }

    protected static String format(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ValidationException::format).sorted().collect(joining(lineSeparator()));
    }
}
