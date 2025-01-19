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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import jakarta.annotation.Nullable;

import java.util.Map;

public interface CorrelationDataProvider {
    default Map<String, String> getCorrelationData() {
        return getCorrelationData(DeserializingMessage.getCurrent());
    }

    default Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage) {
        if (currentMessage == null) {
            return getCorrelationData(null, null);
        }
        return getCorrelationData(currentMessage.getSerializedObject(), currentMessage.getMessageType());
    }

    Map<String, String> getCorrelationData(@Nullable SerializedMessage currentMessage,
                                           @Nullable MessageType messageType);

    default CorrelationDataProvider andThen(CorrelationDataProvider next) {
        CorrelationDataProvider first = this;

        return new CorrelationDataProvider() {
            @Override
            public Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage) {
                Map<String, String> result = first.getCorrelationData(currentMessage);
                result.putAll(next.getCorrelationData(currentMessage));
                return result;
            }

            @Override
            public Map<String, String> getCorrelationData(@Nullable SerializedMessage currentMessage,
                                                          @Nullable MessageType messageType) {
                Map<String, String> result = first.getCorrelationData(currentMessage, messageType);
                result.putAll(next.getCorrelationData(currentMessage, messageType));
                return result;
            }
        };
    }

}
