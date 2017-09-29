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

package io.fluxcapacitor.axonclient.common.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import io.fluxcapacitor.javaclient.common.connection.ServiceUrlBuilder;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.eventsourcing.websocket.WebSocketEventStore;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueService;
import io.fluxcapacitor.javaclient.keyvalue.websocket.WebsocketKeyValueService;
import io.fluxcapacitor.javaclient.tracking.ConsumerService;
import io.fluxcapacitor.javaclient.tracking.ProducerService;
import io.fluxcapacitor.javaclient.tracking.websocket.WebsocketConsumerService;
import io.fluxcapacitor.javaclient.tracking.websocket.WebsocketProducerService;
import org.axonframework.config.Configurer;

public class WebsocketFluxCapacitorConfiguration extends AbstractFluxCapacitorConfiguration {

    public static Configurer configure(Configurer configurer, ApplicationProperties applicationProperties) {
        return new WebsocketFluxCapacitorConfiguration(applicationProperties).configure(configurer);
    }

    public WebsocketFluxCapacitorConfiguration(ApplicationProperties applicationProperties) {
        super(applicationProperties);
    }

    @Override
    protected ConsumerService createConsumerService(MessageType type) {
        return new WebsocketConsumerService(ServiceUrlBuilder.consumerUrl(type, getApplicationProperties()));
    }

    @Override
    protected ProducerService createProducerService(MessageType type) {
        return new WebsocketProducerService(ServiceUrlBuilder.producerUrl(type, getApplicationProperties()));
    }

    @Override
    protected EventStore createEventStore() {
        return new WebSocketEventStore(ServiceUrlBuilder.eventSourcingUrl(getApplicationProperties()),
                                       createKeyValueService());
    }

    protected KeyValueService createKeyValueService() {
        return new WebsocketKeyValueService(ServiceUrlBuilder.keyValueUrl(getApplicationProperties()));
    }
}
