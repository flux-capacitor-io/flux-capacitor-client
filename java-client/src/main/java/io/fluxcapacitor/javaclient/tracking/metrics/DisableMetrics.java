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

package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.AdhocDispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.util.function.Consumer;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.publishing.AdhocDispatchInterceptor.runWithAdhocInterceptor;

public class DisableMetrics implements HandlerInterceptor, BatchInterceptor {
    @Override
    public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> AdhocDispatchInterceptor.runWithAdhocInterceptor(() -> consumer.accept(batch),
                                                                         (message, messageType) -> null, MessageType.METRICS);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return m -> runWithAdhocInterceptor(() -> function.apply(m),
                                            (message, messageType) -> null, MessageType.METRICS);
    }
}