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
    void testRequestWithWrongCorrectType() {
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
