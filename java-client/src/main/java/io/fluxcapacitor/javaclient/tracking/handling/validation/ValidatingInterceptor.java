package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;

@AllArgsConstructor
public class ValidatingInterceptor implements HandlerInterceptor {
    public static final Validator defaultValidator = Jsr380Validator.createDefault();

    private final Validator validator;

    public ValidatingInterceptor() {
        this.validator = defaultValidator;
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Object handler, String consumer) {
        return m -> {
            validator.validate(m.getPayload());
            return function.apply(m);
        };
    }
}
