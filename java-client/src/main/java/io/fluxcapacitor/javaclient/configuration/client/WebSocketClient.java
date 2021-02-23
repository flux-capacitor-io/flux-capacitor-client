/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.javaclient.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.WebSocketEventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.WebsocketKeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.WebsocketSchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.WebsocketTrackingClient;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.UUID;

import static io.fluxcapacitor.javaclient.common.serialization.compression.CompressionAlgorithm.LZ4;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.consumerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.eventSourcingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.keyValueUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.producerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.schedulingUrl;

public class WebSocketClient extends AbstractClient {

    @Getter
    private final Properties properties;

    public static WebSocketClient newInstance(Properties properties) {
        return new WebSocketClient(properties);
    }

    protected WebSocketClient(Properties properties) {
        super(properties.getName(), properties.getId(),
             type -> new WebsocketGatewayClient(producerUrl(type, properties), properties, type),
             type -> new WebsocketTrackingClient(consumerUrl(type, properties), properties, type),
             new WebSocketEventStoreClient(eventSourcingUrl(properties), properties),
             new WebsocketSchedulingClient(schedulingUrl(properties), properties),
             new WebsocketKeyValueClient(keyValueUrl(properties), properties));
        this.properties = properties;
    }

    @Override
    public void shutDown() {
        super.shutDown();
        //Wait some time after closing all websocket sessions. It seems a Session.close() is not synchronous.
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class Properties {
        @NonNull String serviceBaseUrl;
        @NonNull String name;
        @NonNull @Default String id = UUID.randomUUID().toString();
        @Default CompressionAlgorithm compression = LZ4;
        String projectId;
        String typeFilter;
    }
}
