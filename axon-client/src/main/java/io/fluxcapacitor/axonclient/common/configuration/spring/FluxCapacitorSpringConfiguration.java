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
import io.fluxcapacitor.javaclient.configuration.ClientProperties;
import io.fluxcapacitor.javaclient.configuration.InMemoryClientProperties;
import io.fluxcapacitor.javaclient.configuration.websocket.WebSocketClientProperties;
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

    @Bean
    @SuppressWarnings("ConstantConditions")
    public FluxCapacitorConfiguration fluxCapacitorConfiguration(ClientProperties clientProperties) {
        if (clientProperties instanceof InMemoryClientProperties) {
            log.info("Using in-memory Flux Capacitor client.");
            return new InMemoryFluxCapacitorConfiguration(clientProperties.getApplicationName());
        }
        if (clientProperties instanceof WebSocketClientProperties) {
            return new InMemoryFluxCapacitorConfiguration(clientProperties.getApplicationName());
        }
        throw new UnsupportedOperationException("Unsupported client properties: " + clientProperties);
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
