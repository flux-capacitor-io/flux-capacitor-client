package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.isA;

public class GivenWhenThenTest {

    private final TestFixture subject = TestFixture.create(new CommandHandler());

    @Test
    public void testExpectNoEventsAndNoResult() {
        subject.givenNoPriorActivity().whenCommand(new YieldsNoResult()).expectNoEvents().expectNoResult();
    }

    @Test
    public void testExpectResultButNoEvents() {
        subject.givenNoPriorActivity().whenCommand(new YieldsResult()).expectNoEvents().expectResult(isA(String.class));
    }

    @Test
    public void testExpectEventButNoResult() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenNoPriorActivity().whenCommand(command).expectEvents(command).expectNoResult();
    }

    public static class CommandHandler {
        @HandleCommand
        public void handle(YieldsNoResult command) {
            //no op
        }

        @HandleCommand
        public String handle(YieldsResult command) {
            return "result";
        }

        @HandleCommand
        public void handle(YieldsException command) {
            throw new MockException();
        }

        @HandleCommand
        public void handle(YieldsEventAndNoResult command) {
            FluxCapacitor.publishEvent(command);
        }

        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }

        @HandleCommand
        public void handle(YieldsEventAndException command) {
            FluxCapacitor.publishEvent(command);
            throw new MockException();
        }
    }

    @Value
    private static class YieldsNoResult {
    }

    @Value
    private static class YieldsResult {
    }

    @Value
    private static class YieldsException {
    }

    @Value
    private static class YieldsEventAndNoResult {
    }

    @Value
    private static class YieldsEventAndResult {
    }

    @Value
    private static class YieldsEventAndException {
    }

}
