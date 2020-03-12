package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;

import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.defaultValidator;

@AllArgsConstructor
public class ValidatingInterceptor implements HandlerInterceptor {
    private final Validator validator;

    public ValidatingInterceptor() {
        this.validator = defaultValidator;
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler, String consumer) {
        return m -> {
            validator.assertValid(m.getPayload());
            return function.apply(m);
        };
    }
}
