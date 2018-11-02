package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.Given;
import io.fluxcapacitor.javaclient.test.spring.IntegrationTestConfig;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {IntegrationTestConfig.class, SpringIntegrationTest.FooConfig.class, SpringIntegrationTest.BarConfig.class})
class SpringIntegrationTest {
    
    @Autowired
    private Given testFixture;

    @Test
    void testFoo() {
        testFixture.givenNoPriorActivity().whenCommand("command1").expectEvents("event");
    }

    @Test
    void testBar() {
        testFixture.givenNoPriorActivity().whenCommand("command1").expectCommands("command2");
    }

    @Configuration
    static class FooConfig {
        @Bean
        public FooHandler fooHandler() {
            return new FooHandler();
        }
    }
    
    private static class FooHandler {
        @HandleCommand
        public void handle(String command) {
            FluxCapacitor.publishEvent("event");
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
        public void handle(String event) {
            FluxCapacitor.sendAndForgetCommand("command2");
        }
    }
    
}