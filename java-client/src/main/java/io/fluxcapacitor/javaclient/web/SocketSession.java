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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.publishing.Timeout;
import io.fluxcapacitor.javaclient.tracking.handling.Request;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface SocketSession {

    String sessionId();

    default void sendMessage(Object value) {
        sendMessage(value, Guarantee.NONE);
    }

    CompletableFuture<Void> sendMessage(Object value, Guarantee guarantee);

    default <R> CompletionStage<R> sendRequest(Request<R> request) {
        Timeout timeout = ReflectionUtils.getTypeAnnotation(request.getClass(), Timeout.class);
        Duration duration = timeout == null ? Duration.ofSeconds(30)
                : Duration.of(timeout.value(), timeout.timeUnit().toChronoUnit());
        return sendRequest(request, duration);
    }

    <R> CompletionStage<R> sendRequest(Request<R> request, Duration timeout);

    default void sendPing(Object value) {
        sendPing(value, Guarantee.NONE);
    }

    CompletableFuture<Void> sendPing(Object value, Guarantee guarantee);

    default void close() {
        close(Guarantee.NONE);
    }

    default void close(int closeReason) {
        close(closeReason, Guarantee.NONE);
    }

    default CompletableFuture<Void> close(Guarantee guarantee) {
        return close(1000, guarantee);
    }

    CompletableFuture<Void> close(int closeReason, Guarantee guarantee);

    boolean isOpen();
}
