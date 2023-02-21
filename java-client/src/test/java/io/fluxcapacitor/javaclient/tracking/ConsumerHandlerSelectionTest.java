package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.root.RootHandler;
import io.fluxcapacitor.javaclient.tracking.root.level2.Level2Handler;
import io.fluxcapacitor.javaclient.tracking.root.level2.level3.Level3Handler;
import io.fluxcapacitor.javaclient.tracking.root.level2.level3.Level3HandlerWithCustomConsumer;
import org.junit.jupiter.api.Test;

import static java.util.Collections.nCopies;

public class ConsumerHandlerSelectionTest {

    private final TestFixture testFixture = TestFixture.createAsync(
            DefaultFluxCapacitor.builder().addConsumerConfiguration(
                    ConsumerConfiguration.builder().name("non-exclusive").passive(true).exclusive(false).build()),
            new RootHandler(), new Level2Handler(), new Level3Handler(), new Level3HandlerWithCustomConsumer());

    @Test
    void packageAnnotationIsUsedIfAvailable() {
        testFixture.whenEvent("test").expectEvents(new EventReceived(
                RootHandler.class, "root"));
    }

    @Test
    void subPackageAnnotationTrumpsRootPackageAnnotation() {
        testFixture.whenEvent("test").expectEvents(new EventReceived(
                Level2Handler.class, "level2"));
    }

    @Test
    void closestSuperPackageAnnotationIsUsedWhenAvailable() {
        testFixture.whenEvent("test").expectEvents(new EventReceived(
                Level3Handler.class, "level2"));
    }

    @Test
    void handlerAnnotationTrumpsPackageAnnotation() {
        testFixture.whenEvent("test").expectEvents(new EventReceived(
                Level3HandlerWithCustomConsumer.class, "custom"));
    }

    @Test
    void nonExclusiveConsumerGetsItAlso() {
        testFixture.whenEvent("test").expectEvents(new EventReceived(
                Level3HandlerWithCustomConsumer.class, "non-exclusive"));
    }

    @Test
    void sanityCheck() {
        testFixture.whenEvent("test").expectOnlyEvents(nCopies(8, EventReceived.class).toArray());
    }
}
