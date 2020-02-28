package io.fluxcapacitor.common.handling;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.List;

@FunctionalInterface
public interface MethodInvokerFactory<T> {
    HandlerInspector.MethodHandlerInvoker<T> create(Executable executable, Class<?> enclosingType,
                                                    List<ParameterResolver<? super T>> parameterResolvers,
                                                    Class<? extends Annotation> annotation);
}
