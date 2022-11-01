package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Value;
import org.junit.jupiter.api.Test;

class TriggerParameterResolverTest {

    private final TestFixture testFixture = TestFixture.createAsync(new MainHandler(), new ResultHandler());

    @Test
    void triggerAsPayload() {
        testFixture.whenCommand(new TriggerAsPayload("some result"))
                .expectEvents(new ResultReceived("some result", TriggerAsPayload.class));
    }

    @Test
    void triggerAsPayload_multipleTriggers() {
        testFixture.whenCommand(new TriggerAsPayload3("some result"))
                .expectEvents(new ResultReceived("some result", TriggerAsPayload3.class));
    }

    @Test
    void triggerAsMessage() {
        testFixture.whenCommand(new TriggerAsMessage("some result"))
                .expectEvents(new ResultReceived("some result", TriggerAsMessage.class));
    }

    @Test
    void triggerAsMessage_multipleTriggers() {
        testFixture.whenCommand(new TriggerAsMessage2("some result"))
                .expectEvents(new ResultReceived("some result", TriggerAsMessage2.class));
    }

    @Test
    void triggerAsMessageNotHandled() {
        testFixture.whenCommand(new TriggerAsMessageNotHandled("some result")).expectNoEvents();
    }

    @Test
    void triggerError() {
        testFixture.whenCommand(new Throws("some error"))
                .expectEvents(new ResultReceived("some error", Throws.class));
    }

    @Test
    void triggerNotInjectedIfMessageTypeIsWrong() {
        testFixture.whenQuery(new TriggerAsPayload("some result")).expectNoEvents();
    }

    static class MainHandler {
        @HandleCommand
        String handle(HasResult command) {
            return command.getResult();
        }

        @HandleQuery
        String handleQuery(HasResult query) {
            return query.getResult();
        }

        @HandleCommand
        void handle(Throws command) {
            throw new MockException(command.getErrorMessage());
        }
    }

    static class ResultHandler {
        @HandleResult
        void handle(String result, @Trigger(messageType = MessageType.COMMAND) TriggerAsPayload trigger) {
            FluxCapacitor.publishEvent(new ResultReceived(result, trigger.getClass()));
        }

        @HandleResult
        void handle(String result, @Trigger({TriggerAsPayload2.class, TriggerAsPayload3.class}) HasResult trigger) {
            FluxCapacitor.publishEvent(new ResultReceived(result, trigger.getClass()));
        }

        @HandleResult
        void handle(String result, @Trigger({TriggerAsMessage.class, TriggerAsMessage2.class}) Message trigger) {
            FluxCapacitor.publishEvent(new ResultReceived(result, trigger.getPayloadClass()));
        }

        @HandleResult
        void handle(String result, @Trigger(TriggerAsDeserializingMessage.class) DeserializingMessage trigger) {
            FluxCapacitor.publishEvent(new ResultReceived(result, trigger.getPayloadClass()));
        }

        @HandleError
        void handle(Exception error, @Trigger Throws trigger) {
            FluxCapacitor.publishEvent(new ResultReceived(error.getMessage(), trigger.getClass()));
        }
    }



    interface HasResult {
        String getResult();
    }

    @Value
    static class TriggerAsPayload implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsPayload2 implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsPayload3 implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsMessage implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsMessage2 implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsMessageNotHandled implements HasResult {
        String result;
    }

    @Value
    static class TriggerAsDeserializingMessage implements HasResult {
        String result;
    }

    @Value
    static class Throws {
        String errorMessage;
    }

    @Value
    static class ResultReceived {
        String result;
        Class<?> triggerClass;
    }
}