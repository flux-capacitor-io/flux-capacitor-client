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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

public interface MetricsGateway extends HasLocalHandlers {

    default void publish(Object metrics) {
        if (metrics instanceof Message) {
            publish(((Message) metrics).getPayload(), ((Message) metrics).getMetadata());
        } else {
            publish(metrics, Metadata.empty());
        }
    }

    @SneakyThrows
    default void publish(Object payload, Metadata metadata) {
        publish(payload, metadata, Guarantee.NONE);
    }

    CompletableFuture<Void> publish(Object payload, Metadata metadata, Guarantee guarantee);

}
