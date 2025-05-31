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
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.lang.reflect.Type;

/**
 * Represents a response to a {@link SocketRequest} sent over a WebSocket session.
 * <p>
 * Each {@code SocketResponse} is associated with a {@code requestId} that matches it to the originating request. The
 * response either contains a successful result as a {@link JsonNode}, or an error message indicating failure.
 *
 * <p>{@code SocketResponse} instances are typically generated via the static factory methods:
 * <ul>
 *   <li>{@link #success(long, Object)} — wraps a successful response value</li>
 *   <li>{@link #error(long, String)} — represents an error condition for the given request</li>
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> In most cases, {@code SocketRequest}s and {@code SocketResponse}s are created and handled
 * automatically by the {@link SocketSession}. Developers working within a Flux Capacitor system typically do not need
 * to construct them manually. However, understanding their format becomes important when integrating with non-Flux
 * systems—such as a browser-based UI—over WebSocket. In such scenarios, this class defines the structure of a response
 * expected by a Flux client.
 *
 * @see SocketRequest
 * @see SocketSession#sendRequest(Request, java.time.Duration)
 */
@Value
@AllArgsConstructor
public class SocketResponse {

    /**
     * Identifier of the original request that this response corresponds to.
     */
    long requestId;

    /**
     * JSON-encoded response value, or {@code null} in case of an error.
     */
    JsonNode result;

    /**
     * Optional error message if the request failed.
     */
    String error;

    /**
     * Creates a {@code SocketResponse} representing a successful response.
     *
     * @param requestId the ID of the corresponding request
     * @param response  the response object to serialize
     * @return a successful {@code SocketResponse}
     */
    public static SocketResponse success(long requestId, Object response) {
        return new SocketResponse(requestId, JsonUtils.valueToTree(response), null);
    }

    /**
     * Creates a {@code SocketResponse} representing a failed request.
     *
     * @param requestId the ID of the corresponding request
     * @param error     an error message explaining the failure
     * @return an error {@code SocketResponse}
     */
    public static SocketResponse error(long requestId, String error) {
        return new SocketResponse(requestId, null, error);
    }

    /**
     * Indicates whether this socket response is valid.
     * <p>
     * A response is considered valid if it has a positive {@code requestId} and contains either a result or an error
     * message.
     *
     * @return {@code true} if valid, {@code false} otherwise
     */
    boolean isValid() {
        return requestId > 0 && (result != null || error != null);
    }

    /**
     * Deserializes the {@link #result} JSON node into an object of the given type.
     *
     * @param type the target type to convert the result into
     * @return the deserialized object
     */
    public Object deserialize(Type type) {
        return JsonUtils.convertValue(result, type);
    }
}
