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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestAnnotationProcessorTest {

    @Nested
    class GenericResponseTypeTests {
        static class StringRequest implements Request<String> {
        }

        @Test
        void testStringRequest() {
            assertEquals(String.class, new StringRequest().responseType());
        }

        static class GenericListRequest implements Request<List<String>> {
        }

        @Test
        void testGenericListRequest() {
            assertTrue(new GenericListRequest().responseType() instanceof ParameterizedType pt
                       && pt.getActualTypeArguments().length == 1 && pt.getActualTypeArguments()[0] == String.class);
        }

        static abstract class AbstractClassRequest<R> implements Request<R> {
        }

        static class ConcreteClassRequest extends AbstractClassRequest<String> {
        }

        @Test
        void testConcreteClassRequest() {
            assertEquals(String.class, new ConcreteClassRequest().responseType());
        }

        @SuppressWarnings("rawtypes")
        static class RawRequest implements Request {
        }

        @SuppressWarnings("rawtypes")
        static class RawConcreteClassRequest extends AbstractClassRequest {
        }

        @Test
        void testRawRequest() {
            assertEquals(Object.class, new RawRequest().responseType());
            assertEquals(Object.class, new RawConcreteClassRequest().responseType());
        }

        static abstract class BoundRequest<R extends CharSequence> implements Request<R> {
        }

        static class BoundConcreteClassRequest extends BoundRequest<String> {
        }

        @Test
        void testBoundRequest() {
            assertEquals(String.class, new BoundConcreteClassRequest().responseType());
        }

        interface ListResponse<R> extends List<R> {
        }

        static class ListResponseRequest implements Request<ListResponse<String>> {
        }

        @Test
        void testListResponseRequest() {
            assertTrue(new ListResponseRequest().responseType() instanceof ParameterizedType pt
                       && pt.getRawType() == ListResponse.class && pt.getActualTypeArguments()[0] == String.class);
        }
    }

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
                                java.util.concurrent.Future<String> handle(io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.MockQuery query) {
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
        assertThrows(ReflectException.class, () -> Reflect.compile(
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
        ).create().get());
    }

    @Test
    void testPayloadHandlerWithCorrectType() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        Reflect.compile(
                "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                """
                            package io.fluxcapacitor.javaclient.tracking.handling;
                            public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.Request<String> {
                                @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                String handle() {
                                    return "foo";
                                }
                            }
                        """, new CompileOptions().processors(p)
        ).create().get();
    }

    @Test
    void testPayloadHandlerWithWrongType() {
        RequestAnnotationProcessor p = new RequestAnnotationProcessor();
        assertThrows(ReflectException.class, () -> Reflect.compile(
                "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                """
                            package io.fluxcapacitor.javaclient.tracking.handling;
                            public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.Request<String> {
                                @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                Integer handle() {
                                    return 2;
                                }
                            }
                        """, new CompileOptions().processors(p)
        ).create().get());
    }

    @SuppressWarnings("unused")
    static class MockQuery implements Request<String> {
    }

    @Nested
    class ExtendedRequestTests {
        @Test
        void testStringRequestWithCorrectType() {
            RequestAnnotationProcessor p = new RequestAnnotationProcessor();
            Reflect.compile(
                    "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                    """
                                package io.fluxcapacitor.javaclient.tracking.handling;
                                public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.ExtendedRequestTests.StringRequest {
                                    @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                    String handle() {
                                        return "foo";
                                    }
                                }
                            """, new CompileOptions().processors(p)
            ).create().get();
        }

        @Test
        void testStringRequestWithIncorrectType() {
            RequestAnnotationProcessor p = new RequestAnnotationProcessor();
            assertThrows(ReflectException.class, () -> Reflect.compile(
                    "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                    """
                                package io.fluxcapacitor.javaclient.tracking.handling;
                                public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.ExtendedRequestTests.StringRequest {
                                    @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                        Integer handle() {
                                        return 2;
                                    }
                                }
                            """, new CompileOptions().processors(p)
            ).create().get());
        }

        @SuppressWarnings("unused")
        public interface StringRequest extends Request<String> {
        }

        @Test
        void testExtendedRequestWithCorrectType() {
            RequestAnnotationProcessor p = new RequestAnnotationProcessor();
            Reflect.compile(
                    "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                    """
                                package io.fluxcapacitor.javaclient.tracking.handling;
                                public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.ExtendedRequestTests.ExtendedRequest<String> {
                                    @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                    String handle() {
                                        return "foo";
                                    }
                                }
                            """, new CompileOptions().processors(p)
            ).create().get();
        }

        @Test
        void testExtendedRequestWithIncorrectType() {
            RequestAnnotationProcessor p = new RequestAnnotationProcessor();
            assertThrows(ReflectException.class, () -> Reflect.compile(
                    "io.fluxcapacitor.javaclient.tracking.handling.CorrectPayloadHandler",
                    """
                                package io.fluxcapacitor.javaclient.tracking.handling;
                                public class CorrectPayloadHandler implements io.fluxcapacitor.javaclient.tracking.handling.RequestAnnotationProcessorTest.ExtendedRequestTests.ExtendedRequest<String> {
                                    @io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
                                    Integer handle() {
                                        return 2;
                                    }
                                }
                            """, new CompileOptions().processors(p)
            ).create().get());
        }

        @SuppressWarnings("unused")
        public interface ExtendedRequest<R> extends Request<R> {
        }

    }
}
