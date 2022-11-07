package io.fluxcapacitor.common.handling;

import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

@AllArgsConstructor
public abstract class TypedParameterResolver<M> implements ParameterResolver<M> {
    private final Class<?> type;

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, M value, Object target) {
        return type.isAssignableFrom(parameter.getType());
    }
}
