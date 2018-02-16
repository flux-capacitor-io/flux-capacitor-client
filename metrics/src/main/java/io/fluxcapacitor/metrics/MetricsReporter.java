/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.ClientAction;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.client.WebsocketTrackingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleUsage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;

@Slf4j
public abstract class MetricsReporter {

    private final ObjectMapper objectMapper;
    private final WebSocketClient.Properties clientProperties;
    private final Handler<ClientAction> invoker;

    public MetricsReporter(String fluxCapacitorUrl) {
        this.objectMapper = new ObjectMapper();
        this.invoker = HandlerInspector.createHandler(this, HandleUsage.class, Collections.singletonList(p -> c -> c));
        this.clientProperties = new WebSocketClient.Properties("graphiteReporter", fluxCapacitorUrl);
    }

    public void start() {
        String metricsLogUrl = ServiceUrlBuilder.consumerUrl(MessageType.USAGE, clientProperties);
        TrackingClient trackingClient = new WebsocketTrackingClient(metricsLogUrl);
        TrackingUtils.start("metricsReporter", trackingClient, messages -> messages.stream().map(
                this::deserialize).forEach(this::handle));
    }

    private ClientAction deserialize(SerializedMessage message) {
        try {
            return objectMapper.readValue(message.getData().getValue(), ClientAction.class);
        } catch (IOException e) {
            log.error("Failed to deserialize to ClientAction", e);
            throw new IllegalStateException(e);
        }
    }

    private void handle(ClientAction action) {
        try {
            invoker.invoke(action);
        } catch (Exception e) {
            log.error("Failed to invoke method for ClientAction {}", action, e);
            throw new IllegalStateException(e);
        }
    }
}
