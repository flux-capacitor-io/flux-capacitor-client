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

package io.fluxcapacitor.common;

/**
 * Utility class for constructing internal service endpoint paths used to route messages to the Flux platform.
 * <p>
 * This class provides consistent path-building logic for various platform subsystems such as tracking, event sourcing,
 * scheduling, and search. These paths are typically used by internal clients and websocket-based routing layers.
 *
 * <h2>Examples</h2>
 * <ul>
 *     <li>{@code ServicePathBuilder.producerPath(COMMAND)} returns {@code "tracking/publishcommand"}</li>
 *     <li>{@code ServicePathBuilder.searchPath()} returns {@code "search"}</li>
 * </ul>
 */
public class ServicePathBuilder {

    /**
     * Returns the tracking path used to publish messages of the given {@link MessageType}.
     * <p>
     * This endpoint is used for producers of messages (e.g. commands, events, queries).
     *
     * @param messageType the type of message to publish
     * @return a string like {@code "tracking/publishcommand"}
     */
    public static String producerPath(MessageType messageType) {
        return "tracking/publish" + messageType.name().toLowerCase();
    }

    /**
     * Returns the tracking path used to read (track) messages of the given {@link MessageType}.
     * <p>
     * This endpoint is used by consumers of messages.
     *
     * @param messageType the type of message to consume
     * @return a string like {@code "tracking/readcommand"}
     */
    public static String consumerPath(MessageType messageType) {
        return "tracking/read" + messageType.name().toLowerCase();
    }

    /**
     * Returns the service path for the event sourcing subsystem.
     *
     * @return the string {@code "eventSourcing"}
     */
    public static String eventSourcingPath() {
        return "eventSourcing";
    }

    /**
     * Returns the service path for the key-value store subsystem.
     * <p>
     * This subsystem is largely deprecated and maintained only for backward compatibility.
     *
     * @return the string {@code "keyValue"}
     */
    public static String keyValuePath() {
        return "keyValue";
    }

    /**
     * Returns the service path for the search/document store subsystem.
     *
     * @return the string {@code "search"}
     */
    public static String searchPath() {
        return "search";
    }

    /**
     * Returns the service path for the scheduling subsystem.
     *
     * @return the string {@code "scheduling"}
     */
    public static String schedulingPath() {
        return "scheduling";
    }
}
