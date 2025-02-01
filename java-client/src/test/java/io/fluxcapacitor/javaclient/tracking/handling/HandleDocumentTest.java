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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.search.SearchTest.SomeDocument;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

public class HandleDocumentTest {

    final TestFixture testFixture = TestFixture.createAsync();

    @Test
    void handleDocument_class() {
        testFixture.registerHandlers(new Object() {
                    @HandleDocument(SomeDocument.class)
                    void handleClass() {
                        FluxCapacitor.publishEvent("someDocument");
                    }
                }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                .expectEvents("someDocument");
    }

    @Test
    void handleDocument_collectionName() {
        testFixture.registerHandlers(new Object() {
                    @HandleDocument(collectionName = "someDoc")
                    void handleName() {
                        FluxCapacitor.publishEvent("someDocument");
                    }
                }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                .expectEvents("someDocument");
    }

    @Test
    void handleDocument_firstParam() {
        testFixture
                .registerHandlers(new Object() {
                    @HandleDocument
                    void handle(SomeDocument document) {
                        FluxCapacitor.publishEvent("someDocument");
                    }
                })
                .whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                .expectEvents("someDocument");
    }

}
