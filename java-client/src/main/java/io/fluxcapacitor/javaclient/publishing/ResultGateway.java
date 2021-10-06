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

import java.util.concurrent.CompletableFuture;

public interface ResultGateway {

    default void respond(Object response, String target, int requestId) {
        if (response instanceof Message) {
            respond(((Message) response).getPayload(), ((Message) response).getMetadata(), target, requestId, Guarantee.NONE);
        } else {
            respond(response, Metadata.empty(), target, requestId, Guarantee.NONE);
        }
    }

    CompletableFuture<Void> respond(Object payload, Metadata metadata, String target, int requestId, Guarantee guarantee);

}
