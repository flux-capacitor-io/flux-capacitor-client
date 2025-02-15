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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

public interface EventGateway extends HasLocalHandlers {

    @SneakyThrows
    default void publish(Object event) {
        publish(Message.asMessage(event), Guarantee.NONE).get();
    }

    @SneakyThrows
    default void publish(Object payload, Metadata metadata) {
        publish(new Message(payload, metadata), Guarantee.NONE).get();
    }

    CompletableFuture<Void> publish(Message message, Guarantee guarantee);

    void publish(Object... messages);

    CompletableFuture<Void> publish(Guarantee guarantee, Object... messages);
}
