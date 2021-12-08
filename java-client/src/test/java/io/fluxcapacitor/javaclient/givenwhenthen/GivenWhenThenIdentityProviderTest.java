package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.spy;

public class GivenWhenThenIdentityProviderTest {

    @Test
    void testPredictableIdentityProvider() {
        TestFixture.create(spy(new CommandHandler())).givenNoPriorActivity()
                .whenQuery(new GenerateId())
                .expectResult("cfcd2084-95d5-35ef-a6e7-dff9f98764da"::equals);
    }

    @Value
    private static class GenerateId {
    }

    private static class CommandHandler {

        @HandleQuery
        public String handle(GenerateId query) {
            return FluxCapacitor.generateId();
        }
    }
}
