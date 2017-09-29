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

package io.fluxcapacitor.javaclient.common.connection;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ServicePathBuilder;

public class ServiceUrlBuilder {

    public static String producerUrl(MessageType messageType, ApplicationProperties applicationProperties) {
        return buildUrl(applicationProperties, ServicePathBuilder.producerPath(messageType));
    }

    public static String consumerUrl(MessageType messageType, ApplicationProperties applicationProperties) {
        return buildUrl(applicationProperties, ServicePathBuilder.consumerPath(messageType));
    }

    public static String eventSourcingUrl(ApplicationProperties applicationProperties) {
        return buildUrl(applicationProperties, ServicePathBuilder.eventSourcingPath());
    }

    public static String keyValueUrl(ApplicationProperties applicationProperties) {
        return buildUrl(applicationProperties, ServicePathBuilder.keyValuePath());
    }

    public static String schedulingUrl(ApplicationProperties applicationProperties) {
        return buildUrl(applicationProperties, ServicePathBuilder.schedulingPath());
    }

    private static String buildUrl(ApplicationProperties applicationProperties, String path) {
        return String.format("%s/%s?clientId=%s&clientName=%s", applicationProperties.getFluxCapacitorUrl(), path,
                             applicationProperties.getClientId(), applicationProperties.getApplicationName());
    }

}
