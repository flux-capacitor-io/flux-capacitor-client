/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import lombok.experimental.Delegate;

import java.util.function.Function;

@FunctionalInterface
public interface HandlerInterceptor {
    Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                             Handler<DeserializingMessage> handler, String consumer);

    default Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler, String consumer) {
        return new InterceptedHandler(handler, this, consumer);
    }

    default HandlerInterceptor merge(HandlerInterceptor nextInterceptor) {
        return new MergedInterceptor(this, nextInterceptor);
    }

    @AllArgsConstructor
    class InterceptedHandler implements Handler<DeserializingMessage> {

        @Delegate(excludes = ExcludedMethods.class)
        private final Handler<DeserializingMessage> delegate;
        private final HandlerInterceptor interceptor;
        private final String consumer;

        @Override
        public Object invoke(DeserializingMessage message) {
            return interceptor.interceptHandling(delegate::invoke, delegate, consumer).apply(message);
        }

        private interface ExcludedMethods {
            Object invoke(DeserializingMessage message);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    @AllArgsConstructor
    class MergedInterceptor implements HandlerInterceptor {
        private final HandlerInterceptor first, second;

        @Override
        public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                        Handler<DeserializingMessage> handler,
                                                                        String consumer) {
            return first.interceptHandling(second.interceptHandling(function, handler, consumer), handler, consumer);
        }

        @Override
        public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler, String consumer) {
            //this allows the delegate interceptors to initialize themselves
            second.wrap(handler, consumer);
            first.wrap(handler, consumer);
            return HandlerInterceptor.super.wrap(handler, consumer);
        }
    }
}
