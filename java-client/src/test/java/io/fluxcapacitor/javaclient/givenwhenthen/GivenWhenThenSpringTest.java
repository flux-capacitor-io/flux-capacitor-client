package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.spring.FluxCapacitorTestConfig;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {FluxCapacitorTestConfig.class, GivenWhenThenSpringTest.FooConfig.class, GivenWhenThenSpringTest.BarConfig.class})
class GivenWhenThenSpringTest {
    
    @Autowired
    private StreamingTestFixture testFixture;

    @Test
    void testFoo() {
        testFixture.givenNoPriorActivity().whenCommand("command1").expectEvents("event");
    }

    @Test
    void testBar() {
        testFixture.givenNoPriorActivity().whenCommand("command1").expectCommands("command2");
    }

    @Test
    @SneakyThrows
    void testWaitForSlowResultAfterTerminate() {
        CompletableFuture<Object> result = testFixture.getFluxCapacitor().commandGateway().send(new SlowCommand());
        testFixture.getFluxCapacitor().close();
        assertTrue(result.isDone());
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
        public void handle(String event) {
            FluxCapacitor.sendAndForgetCommand("command2");
        }
    }
    
    @Value
    private static class SlowCommand {
    }
    
}