package io.fluxcapacitor.javaclient.common.logging;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@Slf4j
class FluxCapacitorLogbackAppenderTest {

    private final FluxCapacitor fluxCapacitor = TestFixture.create().getFluxCapacitor();

    @BeforeEach
    void setUp() {
        FluxCapacitor.instance.set(fluxCapacitor);
    }

    @AfterEach
    void tearDown() {
        FluxCapacitor.instance.remove();
    }

    @Test
    void testConsoleError() {
        log.error("mock error");
        verify(fluxCapacitor.client().getGatewayClient(MessageType.ERROR)).send(
                argThat((ArgumentMatcher<SerializedMessage>) message ->
                        ConsoleError.class.getName().equals(message.getData().getType())));
    }

    @Test
    void testConsoleWarning() {
        log.warn("mock warning");
        verify(fluxCapacitor.client().getGatewayClient(MessageType.ERROR)).send(
                argThat((ArgumentMatcher<SerializedMessage>) message ->
                        ConsoleWarning.class.getName().equals(message.getData().getType())));
    }
}