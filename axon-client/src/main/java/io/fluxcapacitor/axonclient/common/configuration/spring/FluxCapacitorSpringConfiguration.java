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

package io.fluxcapacitor.axonclient.common.configuration.spring;

import io.fluxcapacitor.axonclient.common.configuration.FluxCapacitorConfiguration;
import io.fluxcapacitor.axonclient.common.configuration.InMemoryFluxCapacitorConfiguration;
import io.fluxcapacitor.axonclient.common.configuration.WebsocketFluxCapacitorConfiguration;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.Configurer;
import org.axonframework.config.EventHandlingConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings({"SpringFacetCodeInspection", "SpringJavaAutowiringInspection"})
@Slf4j
public class FluxCapacitorSpringConfiguration {

    private final WebSocketClient.Properties webSocketClientProperties;

    public FluxCapacitorSpringConfiguration(@Autowired(required = false) WebSocketClient.Properties webSocketClientProperties) {
        this.webSocketClientProperties = webSocketClientProperties;
    }

    @Bean
    @SuppressWarnings("ConstantConditions")
    public FluxCapacitorConfiguration fluxCapacitorConfiguration() {
        if (webSocketClientProperties == null) {
            log.info("Using in-memory Flux Capacitor client.");
            return new InMemoryFluxCapacitorConfiguration();
        }
        return new WebsocketFluxCapacitorConfiguration(webSocketClientProperties);
    }

    @Autowired
    public void initializeAxonConfigurer(FluxCapacitorConfiguration fluxCapacitorConfiguration, Configurer configurer) {
        fluxCapacitorConfiguration.configure(configurer);
    }

    @Autowired
    public void initializeEventHandlingModule(FluxCapacitorConfiguration fluxCapacitorConfiguration,
                                              EventHandlingConfiguration eventHandlingConfiguration) {
        fluxCapacitorConfiguration.configure(eventHandlingConfiguration);
    }

}
