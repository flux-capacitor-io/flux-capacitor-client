package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class LocalHandlerTest {

    private final TestFixture testFixture =
            TestFixture.createAsync(DefaultFluxCapacitor.builder().enableTrackingMetrics(),
                                    new PublishingLocalHandler());

    @Test
    void testMessagePublication() {
        testFixture.whenCommand("a").expectThat(fc -> verify(fc.client().getGatewayClient(COMMAND)).send(any(), any()));
    }

    @Test
    void testMetricsPublication() {
        testFixture.whenCommand("a").expectThat(fc -> verify(fc.client().getGatewayClient(METRICS)).send(any(), any()));
    }

    @Test
    void testNoMessagePublication() {
        testFixture.whenCommand(1)
                .expectThat(fc -> verify(fc.client().getGatewayClient(COMMAND), never()).send(any(), any()));
    }

    @Test
    void testNoMetricsPublication() {
        testFixture.whenCommand(1.1f)
                .expectThat(fc -> verify(fc.client().getGatewayClient(METRICS), never()).send(any(), any()));
    }

    @LocalHandler(logMessage = true, logMetrics = true)
    private static class PublishingLocalHandler {
        @HandleCommand
        String handle(String command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(logMessage = false)
        Integer handle(Integer command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(logMetrics = false)
        Float handle(Float command) {
            return command;
        }
    }

}
