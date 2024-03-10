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

package io.fluxcapacitor.testserver;

import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchDocumentsResult;
import io.fluxcapacitor.common.search.Facet;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.spring.FluxCapacitorTestConfig;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.metrics.DisableMetrics;
import io.fluxcapacitor.javaclient.tracking.metrics.ProcessBatchEvent;
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

@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
        testFixture.whenCommand(new DoSomething()).expectEvents(new DoSomething());
    }

    @Test
    void testSecondOrderEffect() {
        testFixture.whenCommand(new DoSomething()).expectCommands(new DoSomethingElse());
    }

    @Test
    void testFetchLotsOfDocuments() {
        testFixture.given(fc -> {
            fc.documentStore().index("bla1", "test").get();
            fc.documentStore().index("bla2", "test").get();
            fc.documentStore().index("bla3", "test").get();
        }).whenApplying(fc -> fc.documentStore().search("test").lookAhead("bla").stream(2).toList())
                .<List<?>>expectResult(list -> list.size() == 3);
    }

    @Test
    void testFacetsHandlerIncluded() {
        testFixture.given(fc -> fc.documentStore().index(new FacetedObject("bla"), "test").get())
                .whenApplying(fc -> fc.documentStore().search("test").facetStats())
                .<List<FacetStats>>expectResult(list -> list.size() == 1);
    }

    @Test
    void testGetSchedule() {
        Schedule schedule = new Schedule("bla", "test",
                                         testFixture.getCurrentTime().plusSeconds(10));
        testFixture.givenSchedules(schedule)
                .whenApplying(fc -> fc.scheduler().getSchedule("test").orElse(null))
                .expectResult(schedule);
    }

    @Test
    void allowMetrics() {
        final String consumerName = "MetricsBlocked-consumer";
        @Consumer(name = consumerName)
        class Handler {
            @HandleEvent
            void handle(String ignored) {
                FluxCapacitor.search("mock").fetchAll();
            }
        }
        testFixture.registerHandlers(new Handler()).whenEvent("test")
                .expectMetrics(SearchDocumentsResult.Metric.class, SearchDocuments.class)
                .<ProcessBatchEvent>expectMetric(e -> consumerName.equals(e.getConsumer()));
    }

    @Test
    void blockHandlerMetrics() {
        final String consumerName = "MetricsBlocked-consumer";
        @Consumer(name = consumerName, handlerInterceptors = DisableMetrics.class)
        class Handler {
            @HandleEvent
            void handle(String ignored) {
                FluxCapacitor.search("mock").fetchAll();
            }
        }
        testFixture.registerHandlers(new Handler()).whenEvent("test")
                .expectNoMetricsLike(SearchDocumentsResult.Metric.class)
                .expectNoMetricsLike(SearchDocuments.class)
                .<ProcessBatchEvent>expectMetric(e -> consumerName.equals(e.getConsumer()));
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

    @Value
    private static class FacetedObject {
        @Facet
        String something;
    }

}

