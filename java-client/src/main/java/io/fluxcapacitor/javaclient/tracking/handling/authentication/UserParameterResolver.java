package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.handling.ParameterResolver;
import lombok.AllArgsConstructor;

import java.lang.reflect.Parameter;
import java.util.function.Function;

@AllArgsConstructor
public class UserParameterResolver implements ParameterResolver<Object> {
    @Override
    public Function<Object, Object> resolve(Parameter p) {
        if (User.class.isAssignableFrom(p.getType())) {
            return m -> User.current.get();
        }
        return null;
    }
}
