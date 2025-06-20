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

package io.fluxcapacitor.javaclient.test.spring;


import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.LocalClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorCustomizer;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.configuration.spring.SpringHandlerRegistry;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Optional;

/**
 * Spring configuration class for enabling {@link TestFixture}-based testing in full application contexts.
 * <p>
 * This configuration allows integration tests to leverage Flux Capacitor's {@code given-when-then} style
 * testing while still wiring up the entire Spring Boot application.
 * <p>
 * The {@link TestFixture} bean provided here behaves like a production {@code FluxCapacitor} instance,
 * but internally uses a test fixture engine to record and assert behaviors.
 * It is set up in <strong>asynchronous mode</strong> by default.
 *
 * <p><strong>Usage:</strong><br>
 * Include this configuration in your test using Spring's {@code @SpringBootTest}:
 *
 * <pre>{@code
 * @SpringBootTest(classes = {App.class, FluxCapacitorTestConfig.class})
 * class MyIntegrationTest {
 *
 *     @Autowired TestFixture fixture;
 *
 *     @Test
 *     void testSomething() {
 *         fixture.givenCommands("fixtures/setup-command.json")
 *                .whenCommand("commands/my-command.json")
 *                .expectResult("results/expected-response.json");
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Enabling synchronous fixture mode:</strong><br>
 * To test with a synchronous {@code TestFixture} (where all handlers are invoked in the same thread),
 * set the following property in {@code src/test/resources/application.properties} or override it per test:
 *
 * <pre>{@code
 * fluxcapacitor.test.sync=true
 * }</pre>
 *
 * Or override it in a single test using:
 *
 * <pre>{@code
 * @SpringBootTest(classes = {App.class, FluxCapacitorTestConfig.class})
 * @TestPropertySource(properties = "fluxcapacitor.test.sync=true")
 * class MySyncTest { ... }
 * }</pre>
 *
 * <p>This configuration:
 * <ul>
 *   <li>Imports {@link FluxCapacitorSpringConfig} for automatic handler registration.</li>
 *   <li>Registers a primary {@link FluxCapacitor} bean backed by the test fixture.</li>
 *   <li>Supports customization via {@link FluxCapacitorCustomizer}s.</li>
 *   <li>Falls back to creating an in-memory {@link LocalClient} if no client bean is available in the context.</li>
 *   <li>Supports both synchronous and asynchronous execution modes (see {@code fluxcapacitor.test.sync}).</li>
 * </ul>
 *
 * @see TestFixture
 * @see FluxCapacitorSpringConfig
 * @see FluxCapacitorCustomizer
 */
@Configuration
@RequiredArgsConstructor
@Import(FluxCapacitorSpringConfig.class)
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("!main")
public class FluxCapacitorTestConfig {

    private final ApplicationContext context;

    @Value("${fluxcapacitor.test.sync:false}")
    private boolean synchronous;

    /**
     * Registers a {@link FluxCapacitor} bean backed by the {@link TestFixture}.
     * <p>
     * This allows your Spring application and test components to inject the fixture transparently
     * wherever a {@code FluxCapacitor} is expected.
     */
    @Bean
    @Primary
    public FluxCapacitor testFluxCapacitor(TestFixture testFixture) {
        log.info("Using test fixture for Flux Capacitor");
        return testFixture.getFluxCapacitor();
    }

    /**
     * Constructs an asynchronous {@link TestFixture} using a configured {@link FluxCapacitorBuilder}.
     * <p>
     * If a {@link Client} or {@link WebSocketClient.ClientConfig} is present in the Spring context,
     * it will be used to initialize the fixture. Otherwise, a local in-memory client will be used.
     * <p>
     * All {@link FluxCapacitorCustomizer}s found in the context will be applied to the builder before creation.
     */
    @Bean
    public TestFixture testFixture(FluxCapacitorBuilder fluxCapacitorBuilder, List<FluxCapacitorCustomizer> customizers) {
        fluxCapacitorBuilder.makeApplicationInstance(false);
        FluxCapacitorCustomizer customizer = customizers.stream()
                .reduce((first, second) -> b -> second.customize(first.customize(b)))
                .orElse(b -> b);
        fluxCapacitorBuilder = customizer.customize(fluxCapacitorBuilder);
        Client client = getBean(Client.class).orElseGet(() -> getBean(WebSocketClient.ClientConfig.class).<Client>map(
                WebSocketClient::newInstance).orElse(null));
        if (client == null) {
            return synchronous
                    ? TestFixture.create(fluxCapacitorBuilder)
                    : TestFixture.createAsync(fluxCapacitorBuilder);
        }
        return synchronous
                ? TestFixture.create(fluxCapacitorBuilder)
                : TestFixture.createAsync(fluxCapacitorBuilder, client);
    }

    @Bean
    SpringHandlerRegistry springHandlerRegistry(TestFixture testFixture) {
        return handlers -> {
            testFixture.registerHandlers(handlers);
            return Registration.noOp();
        };
    }

    /**
     * Helper method to retrieve a single bean of the given type from the Spring context, if available.
     */
    protected <T> Optional<T> getBean(Class<T> type) {
        return context.getBeansOfType(type).values().stream().findFirst();
    }
}
