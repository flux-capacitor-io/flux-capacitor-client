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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.spring.FluxCapacitorTestConfig;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;

import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@Disabled("Leave here to enable tests against real server")
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {FluxCapacitorTestConfig.class, GivenWhenThenSpringCustomClientTest.FooConfig.class, GivenWhenThenSpringCustomClientTest.BarConfig.class})
@Slf4j
class GivenWhenThenSpringCustomClientTest {

    @Autowired
    private TestFixture testFixture;

    @Test
    void testFoo() {
        testFixture.givenNoPriorActivity().whenCommand(new DoSomething()).expectEvents(new DoSomething());
    }

    @Test
    void testBar() {
        testFixture.givenNoPriorActivity().whenCommand(new DoSomething()).expectCommands(new DoSomethingElse());
    }

    @Configuration
    static class FooConfig {
        @Bean
        public WebSocketClient.Properties webSocketClientProperties() {
            return WebSocketClient.Properties.builder()
                    .serviceBaseUrl("http://localhost:8080")
                    .projectId("clienttest")
                    .name("GivenWhenThenSpringCustomClientTest")
                    .build();
        }

        @Bean
        public FooHandler fooHandler() {
            return new FooHandler();
        }
    }

    private static class FooHandler {
        @HandleCommand
        public void handle(DoSomething command) {
            FluxCapacitor.publishEvent(command);
        }

        @HandleCommand
        public CompletableFuture<?> handle(SlowCommand command) {
            return CompletableFuture.runAsync(this::sleepAWhile);
        }

        @SneakyThrows
        private void sleepAWhile() {
            Thread.sleep(500);
        }
    }

    @Configuration
    static class BarConfig {
        @Bean
        public BarHandler barHandler() {
            return new BarHandler();
        }
    }

    static class BarHandler {
        @HandleEvent
        public void handle(DoSomething event) {
            FluxCapacitor.sendAndForgetCommand(new DoSomethingElse());
        }
    }

    @Value
    private static class DoSomething {
    }

    @Value
    private static class DoSomethingElse {
    }

    @Value
    private static class SlowCommand {
    }

    @SneakyThrows
    private static void sleepAWhile() {
        Thread.sleep(500);
    }

}