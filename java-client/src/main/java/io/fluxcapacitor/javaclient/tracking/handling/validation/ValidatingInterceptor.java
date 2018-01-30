package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;
import java.util.function.Function;

@AllArgsConstructor
public class ValidatingInterceptor implements HandlerInterceptor {
    private final Validator validator;

    public ValidatingInterceptor() {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();;
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return m -> {
            Set<? extends ConstraintViolation<?>> violations = validator.validate(m.getPayload());
            if (!violations.isEmpty()) {
                throw new ValidationException(violations);
            }
            return function.apply(m);
        };
    }
}
