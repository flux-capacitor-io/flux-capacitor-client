package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.IgnoringErrorHandler;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static org.mockito.Mockito.spy;

class GivenWhenThenStreamingTest {

    private final CommandHandler commandHandler = spy(new CommandHandler());
    private final AsyncCommandHandler asyncCommandHandler = spy(new AsyncCommandHandler());
    private final EventHandler eventHandler = spy(new EventHandler());
    private final StreamingTestFixture
            subject = StreamingTestFixture.create(DefaultFluxCapacitor.builder().configureDefaultConsumer(
                    COMMAND, config -> config.toBuilder().errorHandler(new IgnoringErrorHandler()).build()), 
                                                  commandHandler, eventHandler, asyncCommandHandler);

    @Test
    void testExpectCommandsAndIndirectEvents() {
        subject.whenEvent(123).expectNoResult().expectCommands(new YieldsEventAndResult()).expectEvents(new YieldsEventAndResult());
    }

    @Test
    void testExpectFunctionalException() {
        subject.whenCommand(new YieldsException()).expectException(MockException.class);
    }

    @Test
    void testExpectTechnicalException() {
        subject.whenCommand(new YieldsRuntimeException()).expectException(TechnicalException.class);
    }

    @Test
    void testAsyncCommandHandling() {
        subject.whenCommand(new YieldsAsyncResult()).expectResult("test");
    }

    @Test
    void testExpectPassiveHandling() {
        subject.givenNoPriorActivity().whenCommand(new PassivelyHandled()).expectException(TimeoutException.class);
    }

    private static class CommandHandler {
        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }

        @HandleCommand
        public void handle(YieldsException command) {
            throw new MockException("expected");
        }

        @HandleCommand
        public void handle(YieldsRuntimeException command) {
            throw new IllegalStateException("expected");
        }

        @HandleCommand(passive = true)
        public String handle(PassivelyHandled command) {
            return "this will be ignored";
        }
    }

    private static class EventHandler {
        @HandleEvent
        public void handle(Integer event) throws Exception {
            FluxCapacitor.sendCommand(new YieldsEventAndResult()).get();
        }
    }
    
    private static class AsyncCommandHandler {
        
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        @HandleCommand
        public CompletableFuture<String> handle(YieldsAsyncResult command) {
            CompletableFuture<String> result = new CompletableFuture<>();
            scheduler.schedule(() -> result.complete("test"), 10, TimeUnit.MILLISECONDS);
            return result;
        }
    }
    
    @Value
    private static class YieldsEventAndResult {
    }

    @Value
    private static class YieldsAsyncResult {
    }

    @Value
    private static class YieldsException {
    }

    @Value
    private static class YieldsRuntimeException {
    }

    @Value
    private static class PassivelyHandled {
    }

}
