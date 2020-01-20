package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.reflect.Executable;
import java.util.List;

@Value
@Builder
public class HandlerConfiguration<T> {
    @Default
    @Accessors(fluent = true)
    boolean failOnMissingMethods = true;
    @Default
    @Accessors(fluent = true)
    boolean invokeMultipleMethods = false;
    @Default
    MethodInvokerFactory<T> invokerFactory = MethodHandlerInvoker::new;
    
    public static <T> HandlerConfiguration<T> defaultHandlerConfiguration() {
        return HandlerConfiguration.<T>builder().build();
    }

    @FunctionalInterface
    interface MethodInvokerFactory<T> {
        MethodHandlerInvoker<T> create(Executable executable, Class<?> enclosingType,
                                       List<ParameterResolver<? super T>> parameterResolvers);
    }
}
