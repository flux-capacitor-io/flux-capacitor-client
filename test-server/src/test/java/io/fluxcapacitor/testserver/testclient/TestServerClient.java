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

package io.fluxcapacitor.testserver.testclient;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.System.getProperty;

@Slf4j
@Configuration
@ComponentScan
@Import(FluxCapacitorSpringConfig.class)
class TestServerClient {

    public static void main(String... args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestServerClient.class);
        context.registerShutdownHook();

        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(() -> FluxCapacitor.publishEvent(UUID.randomUUID().toString()), 0, 1,
                                        TimeUnit.SECONDS);
    }

    @Bean
    public Client fluxCapacitorClient() {
        return WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder().name("testapp")
                                                   .serviceBaseUrl(getProperty("endpoint.messaging", "ws://localhost:8888")).build());
    }

    @Component
    public static class TestEventHandler {
        @HandleEvent
        void handle(Object event) {
            log.info("Got event: {}", event);
        }
    }

}

