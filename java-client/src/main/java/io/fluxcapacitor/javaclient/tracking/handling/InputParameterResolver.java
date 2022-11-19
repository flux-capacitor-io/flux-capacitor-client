package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.ParameterResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

public class InputParameterResolver implements ParameterResolver<Object> {
    @Override
    public Function<Object, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
        return i -> i;
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value, Object target) {
        return value != null && parameter.getType().isAssignableFrom(value.getClass());
    }
}
