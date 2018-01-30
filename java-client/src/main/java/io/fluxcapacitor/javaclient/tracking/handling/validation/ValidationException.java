package io.fluxcapacitor.javaclient.tracking.handling.validation;

import lombok.Getter;

import javax.validation.ConstraintViolation;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@Getter
public class ValidationException extends RuntimeException {

    private final Set<? extends ConstraintViolation<?>> violations;

    public ValidationException(Set<? extends ConstraintViolation<?>> violations) {
        super(String.format("One or more constraints were violated:%s%s", System.lineSeparator(), convert(violations)));
        this.violations = violations;
    }

    protected static String convert(Set<? extends ConstraintViolation<?>> violations) {
        return String.join(System.lineSeparator(), violations.stream().map(v -> String
                .format("property %s in class %s %s", v.getPropertyPath(), v.getRootBeanClass().getSimpleName(),
                        v.getMessage())).sorted().collect(toList()));
    }
}
