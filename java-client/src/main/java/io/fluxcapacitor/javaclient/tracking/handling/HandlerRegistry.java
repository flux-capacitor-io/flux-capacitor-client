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

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface HandlerRegistry extends HasLocalHandlers {

    Optional<CompletableFuture<Object>> handle(DeserializingMessage message);

    default HandlerRegistry merge(HandlerRegistry next) {
        return new MergedHandlerRegistry(this, next);
    }

    @AllArgsConstructor
    class MergedHandlerRegistry implements HandlerRegistry {
        private final HandlerRegistry first, second;

        @Override
        public Optional<CompletableFuture<Object>> handle(DeserializingMessage message) {
            Optional<CompletableFuture<Object>> firstResult = first.handle(message);
            Optional<CompletableFuture<Object>> secondResult = second.handle(message);
            return firstResult.isPresent() ? secondResult.map(messageCompletableFuture -> firstResult.get()
                    .thenCombine(messageCompletableFuture, (a, b) -> a)).or(() -> firstResult) : secondResult;
        }

        @Override
        public Registration registerHandler(Object target) {
            return first.registerHandler(target).merge(second.registerHandler(target));
        }

        @Override
        public void setSelfHandlerFilter(HandlerFilter selfHandlerFilter) {
            first.setSelfHandlerFilter(selfHandlerFilter);
            second.setSelfHandlerFilter(selfHandlerFilter);
        }

        @Override
        public Registration registerHandler(Object target, HandlerFilter handlerFilter) {
            return first.registerHandler(target, handlerFilter).merge(second.registerHandler(target, handlerFilter));
        }
    }
}
