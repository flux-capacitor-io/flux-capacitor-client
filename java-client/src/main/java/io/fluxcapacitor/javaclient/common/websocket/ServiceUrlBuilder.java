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

/**
 * Utility class for constructing fully qualified service endpoint URLs for various Flux Platform features
 * based on the client's configuration.
 * <p>
 * This class supports generating URLs for services such as message production and consumption, event sourcing,
 * key-value storage, search, and scheduling. It combines static path segments (from {@link ServicePathBuilder})
 * with dynamic query parameters like client ID, name, compression settings, and topic filters from the provided
 * {@link WebSocketClient.ClientConfig}.
 *
 * <p>This class is typically used internally by WebSocket-based gateway clients to determine which platform
 * endpoint to connect to.
 *
 * @see WebSocketClient.ClientConfig
 * @see ServicePathBuilder
 */
public class ServiceUrlBuilder {

    /**
     * Builds the URL to send messages to a gateway endpoint for the specified message type and topic.
     *
     * @param messageType   the type of message (e.g., COMMAND, EVENT, QUERY)
     * @param topic         the topic name (optional, may be null)
     * @param clientConfig  the WebSocket client configuration
     * @return the complete gateway URL with query parameters
     */
    public static String gatewayUrl(MessageType messageType, String topic, WebSocketClient.ClientConfig clientConfig) {
        String url = buildUrl(clientConfig, ServicePathBuilder.gatewayPath(messageType));
        if (topic != null) {
            url += "&topic=" + URLEncoder.encode(topic, UTF_8);
        }
        return url;
    }

    /**
     * Builds the URL to subscribe to messages from a tracking endpoint for the specified message type and topic.
     *
     * @param messageType   the type of message (e.g., COMMAND, EVENT, QUERY)
     * @param topic         the topic to subscribe to (optional)
     * @param clientConfig  the WebSocket client configuration
     * @return the complete tracking URL with query parameters
     */
    public static String trackingUrl(MessageType messageType, String topic, WebSocketClient.ClientConfig clientConfig) {
        String url = buildUrl(clientConfig, ServicePathBuilder.trackingPath(messageType));
        if (topic != null) {
            url += "&topic=" + URLEncoder.encode(topic, UTF_8);
        }
        if (clientConfig.getTypeFilter() != null) {
            url += "&typeFilter=" + clientConfig.getTypeFilter();
        }
        return url;
    }

    /**
     * Constructs the URL for accessing the event sourcing stream.
     *
     * @param clientConfig the WebSocket client configuration
     * @return the complete URL for the event sourcing service
     */
    public static String eventSourcingUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.eventSourcingPath());
    }

    /**
     * Constructs the URL for accessing the distributed key-value store service.
     *
     * @param clientConfig the WebSocket client configuration
     * @return the complete URL for the key-value service
     */
    public static String keyValueUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.keyValuePath());
    }

    /**
     * Constructs the URL for accessing the document and handler state search service.
     *
     * @param clientConfig the WebSocket client configuration
     * @return the complete URL for the search service
     */
    public static String searchUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.searchPath());
    }

    /**
     * Constructs the URL for accessing the time-based message scheduling service.
     *
     * @param clientConfig the WebSocket client configuration
     * @return the complete URL for the scheduling service
     */
    public static String schedulingUrl(WebSocketClient.ClientConfig clientConfig) {
        return buildUrl(clientConfig, ServicePathBuilder.schedulingPath());
    }

    /**
     * Constructs a base URL for a given service path, including query parameters derived from the given client config.
     * <p>
     * This method is used by the service-specific builders above and includes fields such as:
     * <ul>
     *     <li>{@code clientId}</li>
     *     <li>{@code clientName}</li>
     *     <li>{@code projectId} (optional)</li>
     *     <li>{@code compression} algorithm</li>
     * </ul>
     *
     * @param clientConfig the WebSocket client configuration
     * @param path         the service-specific path from {@link ServicePathBuilder}
     * @return the constructed base URL with encoded query parameters
     */
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
