package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.reflect.Executable;
import java.util.function.BiPredicate;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class HandlerConfiguration<T> {

    @Default boolean invokeMultipleMethods = false;
    @Default BiPredicate<Class<?>, Executable> handlerFilter = (c, e) -> true;
    @Default MethodInvokerFactory<T> invokerFactory = MethodHandlerInvoker::new;

    @SuppressWarnings("unchecked")
    public static <T> HandlerConfiguration<T> defaultHandlerConfiguration() {
        return (HandlerConfiguration<T>) builder().build();
    }

    public static <T> HandlerConfiguration<T> localHandlerConfiguration() {
        return HandlerConfiguration.<T>builder().build();
    }

}
