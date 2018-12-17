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

package io.fluxcapacitor.javaclient.common.websocket;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ServicePathBuilder;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;

public class ServiceUrlBuilder {

    public static String producerUrl(MessageType messageType, WebSocketClient.Properties clientProperties) {
        return buildUrl(clientProperties, ServicePathBuilder.producerPath(messageType));
    }

    public static String consumerUrl(MessageType messageType, WebSocketClient.Properties clientProperties) {
        String url = buildUrl(clientProperties, ServicePathBuilder.consumerPath(messageType));
        if (clientProperties.getTypeFilter() != null) {
            url += "&typeFilter=" + clientProperties.getTypeFilter();
        }
        return url;
    }

    public static String eventSourcingUrl(WebSocketClient.Properties clientProperties) {
        return buildUrl(clientProperties, ServicePathBuilder.eventSourcingPath());
    }

    public static String keyValueUrl(WebSocketClient.Properties clientProperties) {
        return buildUrl(clientProperties, ServicePathBuilder.keyValuePath());
    }

    public static String schedulingUrl(WebSocketClient.Properties clientProperties) {
        return buildUrl(clientProperties, ServicePathBuilder.schedulingPath());
    }

    private static String buildUrl(WebSocketClient.Properties clientProperties, String path) {
        if (clientProperties.getProjectId() != null) {
            return String.format("%s/%s?projectId=%s&clientId=%s&clientName=%s",
                                 clientProperties.getServiceBaseUrl(), path, clientProperties.getProjectId(),
                                 clientProperties.getId(), clientProperties.getName());
        }
        return String.format("%s/%s?clientId=%s&clientName=%s",
                             clientProperties.getServiceBaseUrl(), path,
                             clientProperties.getId(), clientProperties.getName());
    }

}
