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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

public interface ErrorGateway extends HasLocalHandlers {

    default void report(Object error) {
        if (error instanceof Message) {
            report(((Message) error).getPayload(), ((Message) error).getMetadata());
        } else {
            report(error, Metadata.empty());
        }
    }

    default void report(Object error, String target) {
        if (error instanceof Message) {
            report(((Message) error).getPayload(), ((Message) error).getMetadata(), target);
        } else {
            report(error, Metadata.empty(), target);
        }
    }

    default void report(Object payload, Metadata metadata) {
        report(payload, metadata, null);
    }

    void report(Object payload, Metadata metadata, String target);

}
