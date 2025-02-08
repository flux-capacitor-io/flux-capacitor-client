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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.search.SearchTest.SomeDocument;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

public class HandleDocumentTest {

    @Nested
    class SyncTests extends TestMethods {
        public SyncTests() {
            super(TestFixture.create());
        }
    }

    @Nested
    class AsyncTests extends TestMethods {
        public AsyncTests() {
            super(TestFixture.createAsync());
        }
    }

    @AllArgsConstructor
    private abstract static class TestMethods {
        TestFixture testFixture;

        @Test
        void handleDocument_class() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument(documentClass = SomeDocument.class)
                        void handleClass() {
                            FluxCapacitor.publishEvent("someDocument");
                        }

                        @HandleDocument("otherDoc")
                        void handleName() {
                            FluxCapacitor.publishEvent("otherDocument");
                        }
                    }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                    .expectOnlyEvents("someDocument");
        }

        @Test
        void handleDocument_collectionName() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument("someDoc")
                        void handleName() {
                            FluxCapacitor.publishEvent("someDocument");
                        }

                        @HandleDocument("otherDoc")
                        void handleOther() {
                            FluxCapacitor.publishEvent("otherDocument");
                        }
                    }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                    .expectOnlyEvents("someDocument")
                    .andThen()
                    .whenExecuting(fc -> FluxCapacitor.index("foo", "otherDoc").get())
                    .expectOnlyEvents("otherDocument");
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
                    .expectTrue(fc -> FluxCapacitor.search(SomeDocument.class).count() == 1)
                    .expectEvents("someDocument");
        }

        @Test
        void deletingDocument() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument
                        Object handle(SomeDocument doc) {
                            return null;
                        }
                    }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                    .expectNoErrors()
                    .expectTrue(fc -> FluxCapacitor.search(SomeDocument.class).count() == 0);
        }

        @Test
        void notDeletingDocumentIfReturnTypeIsWrong() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument
                        String handle(SomeDocument doc) {
                            return null;
                        }
                    }).whenExecuting(fc -> FluxCapacitor.index(new SomeDocument()).get())
                    .expectNoErrors()
                    .expectTrue(fc -> FluxCapacitor.search(SomeDocument.class).count() == 1);
        }

        @Test
        void updateRevision() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument
                        MyDocument handleClass(MyDocument document) {
                            return document.toBuilder().value("bar").build();
                        }
                    }).whenExecuting(fc -> {
                        var serializedDocument = fc.documentStore().getSerializer().toDocument(
                                new MyDocument("foo"), "123", MyDocument.class.getSimpleName(), null, null);
                        Data<byte[]> data = serializedDocument.getDocument();
                        serializedDocument = serializedDocument.withData(() -> data.withRevision(0));
                        fc.client().getSearchClient().index(List.of(serializedDocument), Guarantee.STORED, false).get();
                    })
                    .expectTrue(fc -> {
                        var hit =
                                FluxCapacitor.search(MyDocument.class).streamHits(SerializedDocument.class).findFirst()
                                        .orElseThrow();
                        MyDocument document = fc.documentStore().getSerializer().fromDocument(hit.getValue());
                        return hit.getValue().getDocument().getRevision() == 1 && document.getValue().equals("bar");
                    });
        }

        @Test
        void noUpdateIfSameRevision() {
            testFixture.registerHandlers(new Object() {
                        @HandleDocument
                        MyDocument handleClass(MyDocument document) {
                            FluxCapacitor.publishEvent("got here");
                            return document.toBuilder().value("bar").build();
                        }
                    }).whenExecuting(fc -> FluxCapacitor.index(new MyDocument("foo")).get())
                    .expectEvents("got here")
                    .expectFalse(fc -> FluxCapacitor.search(MyDocument.class).<MyDocument>fetchFirst().orElseThrow()
                            .getValue().equals("bar"));
        }

    }

    @Revision(1)
    @Value
    @Builder(toBuilder = true)
    static class MyDocument {
        String value;
    }

}
