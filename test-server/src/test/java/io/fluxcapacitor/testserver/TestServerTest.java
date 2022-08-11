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

package io.fluxcapacitor.testserver;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.persisting.search.client.WebSocketSearchClient;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.spring.FluxCapacitorTestConfig;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@DirtiesContext
@ContextConfiguration(classes = {FluxCapacitorTestConfig.class, TestServerTest.FooConfig.class, TestServerTest.BarConfig.class})
@Slf4j
class TestServerTest {

    private static final int port = 9123;

    @BeforeAll
    static void beforeAll() {
        TestServer.start(port);
    }

    @Autowired
    private TestFixture testFixture;

    @Test
    void testFirstOrderEffect() {
        testFixture.givenNoPriorActivity().whenCommand(new DoSomething()).expectEvents(new DoSomething());
    }

    @Test
    void testSecondOrderEffect() {
        testFixture.givenNoPriorActivity().whenCommand(new DoSomething()).expectCommands(new DoSomethingElse());
    }

    @Test
    void testFetchLotsOfDocuments() {
        int fetchSize = WebSocketSearchClient.maxFetchSize;
        try {
            WebSocketSearchClient.maxFetchSize = 2;
            DocumentStore documentStore = testFixture.getFluxCapacitor().documentStore();
            documentStore.index("bla1", "test");
            documentStore.index("bla2", "test");
            documentStore.index("bla3", "test");
            List<Object> results = documentStore.search("test").lookAhead("bla").fetchAll();
            assertEquals(3, results.size());
        } finally {
            WebSocketSearchClient.maxFetchSize = fetchSize;
        }
    }

    @Test
    void testGetSchedule() {
        Schedule schedule = new Schedule("bla", "test",
                                         testFixture.getClock().instant().plusSeconds(10));
        testFixture.givenSchedules(schedule)
                .given(fc -> sleepAWhile())
                .whenApplying(fc -> fc.scheduler().getSchedule("test").orElse(null))
                .expectResult(schedule);
    }

    @Configuration
    static class FooConfig {
        @Bean
        public WebSocketClient.ClientConfig webSocketClientProperties() {
            return WebSocketClient.ClientConfig.builder()
                    .serviceBaseUrl("ws://localhost:" + port)
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

    @SneakyThrows
    private static void sleepAWhile() {
        Thread.sleep(100);
    }

}

