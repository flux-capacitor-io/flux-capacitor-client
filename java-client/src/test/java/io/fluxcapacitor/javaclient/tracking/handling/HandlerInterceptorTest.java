package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;

class HandlerInterceptorTest {

    @Test
    void modifyResult() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                (f, i, c) -> m -> f.apply(m) + "bar", COMMAND), MockCommandHandler.class)
                .whenCommand("foo")
                .expectEvents("foo").expectResult("foobar").expectNoErrors();
    }

    @Test
    void blockCommand() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i, c) -> m -> null, COMMAND), MockCommandHandler.class)
                .whenCommand("foo")
                .expectNoResult().expectNoEvents().expectNoErrors();
    }

    @Test
    void throwException() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i, c) -> m -> { throw new MockException(); }, COMMAND), MockCommandHandler.class)
                .whenCommand("foo")
                .expectExceptionalResult(MockException.class).expectNoEvents();
    }

    @Test
    void changePayload() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i, c) -> m -> f.apply(new DeserializingMessage(
                                m.toMessage().withPayload("foobar"), COMMAND, new JacksonSerializer())),
                        COMMAND), MockCommandHandler.class)
                .whenCommand("foo")
                .expectEvents("foobar").expectResult("foobar").expectNoErrors();
    }

    @Test
    void changePayloadTypeNotSupported() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i, c) -> m -> f.apply(new DeserializingMessage(
                                m.toMessage().withPayload(123), COMMAND, new JacksonSerializer())),
                        COMMAND), MockCommandHandler.class)
                .whenCommand("foo")
                .expectExceptionalResult(UnsupportedOperationException.class);
    }

    static class MockCommandHandler {
        @HandleCommand
        String handle(String command) {
            FluxCapacitor.publishEvent(command);
            return command;
        }
    }
}