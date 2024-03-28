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
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.joor.CompileOptions;
import org.joor.Reflect;
import org.joor.ReflectException;
import org.junit.jupiter.api.Test;

public class RequestAnnotationProcessorTest {

    @Test
    void testQueryReturnTypeIsFixed() {
        var handler = new Object() {
            @HandleQuery
            String handle(MockQuery query) {
                return "foo";
            }
        };
        TestFixture.create(handler)
                .whenApplying(fc -> FluxCapacitor.queryAndWait(new MockQuery()))
                .expectResult("foo");
    }

    @Test
    void testRequestWithCorrectType() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        Reflect.compile(
                "io.fluxcapacitor.javaclient.tracking.handling.CorrectHandler",
                """
                            package io.fluxcapacitor.javaclient.tracking.handling;
                            public class CorrectHandler {
                                @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                String handle(io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.MockQuery query) {
                                    return "foo";
                                }
                            }
                        """, new CompileOptions().processors(p)
        ).create().get();
    }

    @Test
    void testRequestWithCorrectFutureType() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        Reflect.compile(
                "io.fluxcapacitor.javaclient.tracking.handling.CorrectHandler",
                """
                            package io.fluxcapacitor.javaclient.tracking.handling;
                            public class CorrectHandler {
                                @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                java.util.concurrent.Future<java.lang.String> handle(io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.MockQuery query) {
                                    return java.util.concurrent.CompletableFuture.completedFuture("foo");
                                }
                            }
                        """, new CompileOptions().processors(p)
        ).create().get();
    }

    @Test
    void testRequestWithWrongTypeAllowedIfPassive() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        Reflect.compile(
                "io.fluxcapacitor.javaclient.tracking.handling.PassiveHandler",
                """
                            package io.fluxcapacitor.javaclient.tracking.handling;
                            public class PassiveHandler {
                                @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery(passive = true)
                                Boolean handle(io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.MockQuery query) {
                                    return true;
                                }
                            }
                        """, new CompileOptions().processors(p)
        ).create().get();
    }

    @Test
    void testRequestWithWrongReturnType() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        try {
            Reflect.compile(
                    "io.fluxcapacitor.javaclient.tracking.handling.WrongHandler",
                    """
                                package io.fluxcapacitor.javaclient.tracking.handling;
                                public class WrongHandler {
                                    @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                    Boolean handle(io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.MockQuery query) {
                                        return true;
                                    }
                                }
                            """, new CompileOptions().processors(p)
            ).create().get();
            throw new AssertionError();
        } catch (ReflectException ignored) {
        }
    }

    @SuppressWarnings("unused")
    static class MockQuery implements Request<String> {
    }
}
