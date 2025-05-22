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
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;

/**
 * Functional interface for decorating {@link Handler} instances that process {@link DeserializingMessage} objects.
 * <p>
 * A {@code HandlerDecorator} is used to wrap a handler with additional behavior. This provides a flexible mechanism for
 * implementing cross-cutting concerns such as authorization, metrics, context propagation, or logging.
 * </p>
 *
 * <p>
 * Unlike {@link HandlerInterceptor}, which focuses on message-level interception, a {@code HandlerDecorator} wraps the
 * entire handler object and can be applied at a higher level—for example, before handler resolution occurs.
 * </p>
 *
 * <h2>Composition</h2>
 * <p>
 * Decorators can be chained using {@link #andThen(HandlerDecorator)}, allowing multiple layers of behavior to be
 * composed in a defined order.
 * </p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * public class MetricsDecorator implements HandlerDecorator {
 *     @Override
 *     public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
 *         return new Handler<>() {
 *             @Override
 *             public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
 *                 return handler.getInvoker(message);
 *             }
 *
 *             @Override
 *             public Class<?> getTargetClass() {
 *                 return handler.getTargetClass();
 *             }
 *
 *             @Override
 *             public Object invoke(BiFunction<Object, Object, Object> combiner) {
 *                 long start = System.nanoTime();
 *                 Object result = handler.getInvoker(message).orElseThrow().invoke(combiner);
 *                 metrics.recordLatency(System.nanoTime() - start);
 *                 return result;
 *             }
 *         };
 *     }
 * }
 * }</pre>
 *
 * @see HandlerInterceptor
 */
@FunctionalInterface
public interface HandlerDecorator {

    /**
     * A no-op decorator that returns the original handler unmodified.
     */
    HandlerDecorator noOp = h -> h;

    /**
     * Wraps the given handler with additional behavior.
     *
     * @param handler the original handler to be wrapped
     * @return a decorated handler
     */
    Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler);

    /**
     * Chains this decorator with another, producing a composite decorator.
     * <p>
     * The {@code next} decorator is applied first, followed by this decorator.
     *
     * @param next the decorator to apply before this one
     * @return a combined {@code HandlerDecorator}
     */
    default HandlerDecorator andThen(HandlerDecorator next) {
        return new MergedDecorator(this, next);
    }

    /**
     * A composite decorator that merges two decorators into one. The {@code second} is applied first, then
     * {@code first}.
     */
    @AllArgsConstructor
    class MergedDecorator implements HandlerDecorator {
        private final HandlerDecorator first, second;

        @Override
        public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
            return first.wrap(second.wrap(handler));
        }
    }
}
