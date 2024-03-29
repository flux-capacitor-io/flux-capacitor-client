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

@FunctionalInterface
public interface HandlerDecorator {
    HandlerDecorator noOp = h -> h;

    Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler);

    default HandlerDecorator andThen(HandlerDecorator next) {
        return new MergedDecorator(this, next);
    }

    @AllArgsConstructor
    class MergedDecorator implements HandlerDecorator {
        private final HandlerDecorator first, second;

        @Override
        public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
            return first.wrap(second.wrap(handler));
        }
    }
}
