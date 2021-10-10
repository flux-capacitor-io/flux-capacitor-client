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

package io.fluxcapacitor.javaclient.test.spring;


import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

@Configuration
@AllArgsConstructor
@Import(FluxCapacitorSpringConfig.class)
@Slf4j
public class FluxCapacitorTestConfig {

    private final ApplicationContext context;

    @Bean
    @Primary
    public FluxCapacitor fluxCapacitor(TestFixture testFixture) {
        return testFixture.getFluxCapacitor();
    }

    @Bean
    public TestFixture testFixture(FluxCapacitorBuilder fluxCapacitorBuilder) {
        fluxCapacitorBuilder.makeApplicationInstance(false);
        Client client = getBean(Client.class).orElseGet(() -> getBean(WebSocketClient.ClientConfig.class).<Client>map(
                WebSocketClient::newInstance).orElse(null));
        if (client == null) {
            return TestFixture.createAsync(fluxCapacitorBuilder);
        }
        return TestFixture.createAsync(fluxCapacitorBuilder, client);
    }

    protected <T> Optional<T> getBean(Class<T> type) {
        return context.getBeansOfType(type).values().stream().findFirst();
    }

}
