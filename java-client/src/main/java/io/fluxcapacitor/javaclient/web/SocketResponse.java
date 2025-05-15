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

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.lang.reflect.Type;

@Value
@AllArgsConstructor
public class SocketResponse {
    long requestId;
    JsonNode result;
    String error;

    public static SocketResponse success(long requestId, Object response) {
        return new SocketResponse(requestId, JsonUtils.valueToTree(response), null);
    }

    public static SocketResponse error(long requestId, String error) {
        return new SocketResponse(requestId, null, error);
    }

    boolean isValid() {
        return requestId > 0 && (result != null || error != null);
    }

    public Object deserialize(Type type) {
        return JsonUtils.convertValue(result, type);
    }
}
