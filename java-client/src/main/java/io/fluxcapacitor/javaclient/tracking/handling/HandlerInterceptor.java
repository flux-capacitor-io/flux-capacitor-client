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
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Mechanism that enables modification of a message before it is handled by a handler. A {@link HandlerInterceptor} can
 * also be used to monitor handled messages, block message handling altogether, and/or inspect or modify the return
 * value of a handler.
 */
@FunctionalInterface
public interface HandlerInterceptor extends HandlerDecorator {

    /**
     * Intercepts a message before it's handled. The underlying handler can be invoked using the given {@code function}.
     * <p>
     * Before invoking the handler it is possible to inspect or modify the message. It is also possible to block a
     * message simply by returning a function that returns without invoking the handler.
     * <p>
     * After invoking the handler it is possible to inspect or modify the response.
     * <p>
     * The given {@code invoker} contains information about the underlying handler.
     */
    Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                             HandlerInvoker invoker);

    default Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        return new InterceptedHandler(handler, this);
    }

    @AllArgsConstructor
    class InterceptedHandler implements Handler<DeserializingMessage> {

        @Delegate(excludes = ExcludedMethods.class)
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

        private interface ExcludedMethods {
            Optional<HandlerInvoker> findInvoker(DeserializingMessage message);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
