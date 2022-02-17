package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;

class ReadOnlyAggregateRootTest {

    @Test
    void testApplyForbidden() {
        TestFixture.create()
                .whenApplying(fc -> loadAggregate("test", Object.class).makeReadOnly().apply("whatever"))
                .expectException(UnsupportedOperationException.class);
    }
}