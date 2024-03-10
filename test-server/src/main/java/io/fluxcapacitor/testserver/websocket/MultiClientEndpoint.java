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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.MemoizingFunction;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@Slf4j
public class MultiClientEndpoint extends Endpoint {

    private final MemoizingFunction<String, Endpoint> endpointSupplier;

    public MultiClientEndpoint(Function<String, Endpoint> endpointSupplier) {
        this.endpointSupplier = memoize(endpointSupplier);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        endpointSupplier.apply(getProjectId(session)).onOpen(session, config);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        endpointSupplier.apply(getProjectId(session)).onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        endpointSupplier.apply(getProjectId(session)).onError(session, thr);
    }

    private String getProjectId(Session session) {
        return Optional.ofNullable(session.getRequestParameterMap().get("projectId")).map(list -> list.get(0))
                .orElse("public");
    }
}
