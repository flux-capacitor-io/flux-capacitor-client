/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
