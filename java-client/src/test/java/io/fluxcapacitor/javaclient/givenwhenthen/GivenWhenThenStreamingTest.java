package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.spy;

class GivenWhenThenStreamingTest {

    private final CommandHandler commandHandler = spy(new CommandHandler());
    private final EventHandler eventHandler = spy(new EventHandler());
    private final StreamingTestFixture
            subject = StreamingTestFixture.create(commandHandler, eventHandler);
    
    @Test
    void testExpectCommandsAndIndirectEvents() {
        subject.whenEvent(123).expectNoResult().expectCommands(new YieldsEventAndResult()).expectEvents(new YieldsEventAndResult());
    }

    private static class CommandHandler {
        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }
    }

    private static class EventHandler {
        @HandleEvent
        public void handle(Integer event) throws Exception {
            FluxCapacitor.sendCommand(new YieldsEventAndResult()).get();
        }
    }

    @Value
    private static class YieldsNoResult {
    }

    @Value
    private static class YieldsEventAndResult {
    }

}
