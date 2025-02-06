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

package io.fluxcapacitor.javaclient.common.websocket;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ServicePathBuilder;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;

import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ServiceUrlBuilder {

    public static String producerUrl(MessageType messageType, String topic, WebSocketClient.ClientConfig clientConfig) {
        String url = buildUrl(clientConfig, ServicePathBuilder.producerPath(messageType));
        if (topic != null) {
            url += "&topic=" + URLEncoder.encode(topic, UTF_8);
        }
        return url;
    }

    public static String consumerUrl(MessageType messageType, String topic, WebSocketClient.ClientConfig clientConfig) {
        String url = buildUrl(clientConfig, ServicePathBuilder.consumerPath(messageType));
        if (topic != null) {
            url += "&topic=" + URLEncoder.encode(topic, UTF_8);
        }
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
                                      URLEncoder.encode(clientConfig.getId(), UTF_8),
                                      URLEncoder.encode(clientConfig.getName(), UTF_8));
        if (clientConfig.getProjectId() != null) {
            result = String.format("%s&projectId=%s", result, URLEncoder.encode(clientConfig.getProjectId(), UTF_8));
        }
        result = String.format("%s&compression=%s", result, clientConfig.getCompression());
        return result;
    }

}
