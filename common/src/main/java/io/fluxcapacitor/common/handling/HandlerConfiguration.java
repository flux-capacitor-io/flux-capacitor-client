package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.Accessors;

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

}