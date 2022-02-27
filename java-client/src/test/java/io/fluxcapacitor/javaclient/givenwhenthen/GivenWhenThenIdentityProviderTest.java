package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.test.PredictableUuidFactory;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.generateId;

public class GivenWhenThenIdentityProviderTest {

    @Test
    void testPredictableIdentityProvider() {
        TestFixture.create().whenApplying(fc -> List.of(generateId(), generateId()))
                .expectResult(List.of("0", "1")::equals);
    }

    @Test
    void testPredictableUuidProvider() {
        TestFixture.create().withIdentityProvider(new PredictableUuidFactory())
                .whenApplying(fc -> List.of(generateId(), generateId()))
                .expectResult(List.of("cfcd2084-95d5-35ef-a6e7-dff9f98764da",
                                      "c4ca4238-a0b9-3382-8dcc-509a6f75849b")::equals);
    }
}
