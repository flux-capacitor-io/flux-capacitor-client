/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerInvoker.DelegatingHandlerInvoker;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Intercepts individual message handling operations, enabling cross-cutting behavior around handler invocation.
 * <p>
 * A {@code HandlerInterceptor} can be used to inspect or modify messages before they are passed to a handler, monitor
 * and log handler executions, block certain messages from being handled, or inspect and modify the return value after
 * handling.
 * </p>
 *
 * <p>
 * Interceptors are typically configured via
 * {@link io.fluxcapacitor.javaclient.tracking.Consumer#handlerInterceptors()}, or applied programmatically using the
 * {@link #wrap(Handler)} method.
 * </p>
 *
 * <h2>Common Use Cases:</h2>
 * <ul>
 *   <li>Validating or transforming a message before it reaches the handler</li>
 *   <li>Adding logging, tracing, or metrics for observability</li>
 *   <li>Conditionally suppressing handler invocation</li>
 *   <li>Decorating or modifying the result of a handler method</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * public class LoggingHandlerInterceptor implements HandlerInterceptor {
 *     @Override
 *     public Function<DeserializingMessage, Object> interceptHandling(
 *             Function<DeserializingMessage, Object> next, HandlerInvoker invoker) {
 *         return message -> {
 *             log.info("Before handling: {}", message.getPayload());
 *             Object result = next.apply(message);
 *             log.info("After handling: {}", result);
 *             return result;
 *         };
 *     }
 * }
 * }</pre>
 *
 * @see io.fluxcapacitor.javaclient.tracking.Consumer#handlerInterceptors()
 * @see BatchInterceptor
 */
@FunctionalInterface
public interface HandlerInterceptor extends HandlerDecorator {

    /**
     * Intercepts the message handling logic.
     * <p>
     * The {@code function} parameter represents the next step in the handling chain— typically the actual message
     * handler. The {@code invoker} provides metadata and invocation logic for the underlying handler method.
     * </p>
     *
     * <p>
     * Within this method, an interceptor may:
     * <ul>
     *   <li>Modify the {@code DeserializingMessage} before passing it to the handler</li>
     *   <li>Bypass the handler entirely and return a value directly</li>
     *   <li>Wrap the result after the handler is invoked</li>
     * </ul>
     *
     * <p>
     * Note: Interceptors may return a different {@code DeserializingMessage}, but it must be compatible
     * with a handler method in the same target class. If no suitable handler is found, an exception will be thrown.
     * </p>
     *
     * @param function the next step in the handler chain (typically the handler itself)
     * @param invoker  the metadata and execution strategy for the actual handler method
     * @return a decorated function that wraps handling behavior
     */
    Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                             HandlerInvoker invoker);

    /**
     * Wraps a {@link Handler} with this interceptor, producing an intercepted handler.
     *
     * @param handler the original handler to wrap
     * @return an intercepted handler that applies this interceptor to all handled messages
     */
    default Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        return new InterceptedHandler(handler, this);
    }

    /**
     * Implementation of {@link Handler} that delegates to another handler and applies a {@code HandlerInterceptor}.
     */
    @AllArgsConstructor
    class InterceptedHandler implements Handler<DeserializingMessage> {
        private final Handler<DeserializingMessage> delegate;
        private final HandlerInterceptor interceptor;

        @Override
        public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
            Optional<HandlerInvoker> invoker = delegate.getInvoker(message);
            return invoker.map(s -> new DelegatingHandlerInvoker(s) {
                @Override
                public Object invoke(BiFunction<Object, Object, Object> combiner) {
                    return interceptor.interceptHandling(m -> {
                        if (m != message) {
                            var i = InterceptedHandler.this.delegate.getInvoker(m)
                                    .orElseThrow(() -> new UnsupportedOperationException(
                                            "Changing the payload type in a HandlerInterceptor is not supported."));
                            return m.apply(msg -> i.invoke(combiner));
                        }
                        return s.invoke(combiner);
                    }, s).apply(message);
                }
            });
        }

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
