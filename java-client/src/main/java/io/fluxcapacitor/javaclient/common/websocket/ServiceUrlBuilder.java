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

package io.fluxcapacitor.javaclient.common.websocket;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ServicePathBuilder;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;

public class ServiceUrlBuilder {

    public static String producerUrl(MessageType messageType, WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.producerPath(messageType));
    }

    public static String consumerUrl(MessageType messageType, WebSocketClient.ClientConfig clientConfig) {
        String url = buildUrl(clientConfig, ServicePathBuilder.consumerPath(messageType));
        if (clientConfig.getTypeFilter() != null) {
            url += "&typeFilter=" + clientConfig.getTypeFilter();
        }
        return url;
    }

    public static String eventSourcingUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.eventSourcingPath());
    }

    public static String keyValueUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.keyValuePath());
    }

    public static String searchUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.searchPath());
    }

    public static String schedulingUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.schedulingPath());
    }

    public static String buildUrl(WebSocketClient.ClientConfig clientConfig, String path) {
        String result = String.format("%s/%s?clientId=%s&clientName=%s",
                                      clientConfig.getServiceBaseUrl(), path,
                                      clientConfig.getId(), clientConfig.getName());
        if (clientConfig.getProjectId() != null) {
            result = String.format("%s&projectId=%s", result, clientConfig.getProjectId());
        }
        if (clientConfig.getCompression() != null) {
            result = String.format("%s&compression=%s", result, clientConfig.getCompression());
        }
        return result;
    }

}
