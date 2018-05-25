/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

import java.util.function.Function;

@FunctionalInterface
public interface HandlerInterceptor {
    Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                             Object handler, String consumer);

    default HandlerInterceptor merge(HandlerInterceptor outerInterceptor) {
        return (f, h, c) -> outerInterceptor.interceptHandling(interceptHandling(f, h, c), h, c);
    }
    
    default Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler, String consumer) {
        return new InterceptedHandler(this, handler, consumer);
    }
    
    @AllArgsConstructor
    class InterceptedHandler implements Handler<DeserializingMessage> {

        private final HandlerInterceptor interceptor;
        private final Handler<DeserializingMessage> delegate;
        private final String consumer;

        @Override
        public Object invoke(DeserializingMessage message) {
            return interceptor.interceptHandling(delegate::invoke, getTarget(), consumer).apply(message);
        }

        @Override
        public boolean canHandle(DeserializingMessage message) {
            return delegate.canHandle(message);
        }

        @Override
        public Object getTarget() {
            return delegate.getTarget();
        }
    }  
}
