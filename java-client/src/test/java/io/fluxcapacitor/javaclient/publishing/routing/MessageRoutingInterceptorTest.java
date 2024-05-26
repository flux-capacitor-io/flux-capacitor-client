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

package io.fluxcapacitor.javaclient.publishing.routing;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageRoutingInterceptorTest {

    private final MessageRoutingInterceptor subject = new MessageRoutingInterceptor();
    private Integer expectedHash = ConsistentHashing.computeSegment("bar");

    @Nested
    class HandlerTests {
        final Object handler = new Object() {
            @HandleEvent
            @RoutingKey("bar")
            void handle(Foo event) {
                if (Tracker.current().map(tracker -> !ConsistentHashing.fallsInRange(
                        event.bar, tracker.getMessageBatch().getSegment())).orElseThrow()) {
                    throw new MockException();
                }
            }
        };

        final TestFixture testFixture = TestFixture.createAsync(
                DefaultFluxCapacitor.builder().configureDefaultConsumer(EVENT, c -> c.toBuilder()
                        .threads(2).ignoreSegment(true).build()), handler);

        @Test
        void ensureHandlerFiltering() {
            testFixture.whenExecuting(fc -> IntStream.range(0, 64).mapToObj(i -> new Foo(UUID.randomUUID().toString())).forEach(
                    e -> ClientUtils.runSilently(
                            () -> fc.eventGateway().publish(Message.asMessage(e), Guarantee.STORED).get())))
                    .expectNoErrors();
        }
    }

    @Value
    static class Foo {
        String bar;
    }

    @Test
    void testNoSegmentWithoutAnnotation() {
        expectedHash = null;
        testInvocation(new Object());
    }

    @Test
    void testFieldAnnotation() {
        testInvocation(new AnnotationOnField());
    }

    @Test
    void testNonStringAnnotation() {
        expectedHash = ConsistentHashing.computeSegment(String.valueOf(42));
        testInvocation(new NonStringAnnotation(42));
    }

    @Test
    void testStaticFieldAnnotation() {
        testInvocation(new AnnotationOnStaticField());
    }

    @Test
    void testAnnotationOnInterfaceMethod() {
        testInvocation((AnnotationOnInterfaceMethod) () -> "bar");
    }

    @Test
    void testAnnotationOnType() {
        testInvocation(new Message(new AnnotationOnType(), Metadata.of("foo", "bar")));
    }

    @Test
    void testStaticInterfaceFieldAnnotation() {
        testInvocation(new AnnotationOnStaticInterfaceField() {
        });
    }

    @Test
    void testStaticInterfaceMethodAnnotation() {
        testInvocation(new AnnotationOnStaticInterfaceMethod() {
        });
    }

    @Test
    void testDefaultInterfaceMethodAnnotation() {
        testInvocation(new AnnotationOnDefaultInterfaceMethod() {
        });
    }

    @Test
    void testMethodAnnotation() {
        testInvocation(new AnnotationOnMethod());
    }

    @Test
    void testStaticMethodAnnotation() {
        testInvocation(new AnnotationOnStaticMethod());
    }

    @Test
    void testAnnotationOnExtendedField() {
        testInvocation(new AnnotationOnExtendedField());
    }

    @Test
    void testAnnotationOnExtendedMethod() {
        testInvocation(new AnnotationOnExtendedMethod());
    }

    @Test
    void testAnnotationOnMethodWithParametersFails() {
        assertThrows(AssertionFailedError.class, () -> testInvocation(new AnnotationOnWrongMethod()));
    }

    private void testInvocation(Object payload) {
        testInvocation(new Message(payload));
    }

    private void testInvocation(Message message) {
        SerializedMessage result = subject.modifySerializedMessage(new SerializedMessage(
                new Data<>("test".getBytes(), "test", 0, null), Metadata.empty(), "someId",
                Clock.systemUTC().millis()), message, EVENT);
        assertEquals(expectedHash, result.getSegment());
    }

    private static class AnnotationOnField {
        @RoutingKey
        private final Object foo = "bar";
    }

    @Value
    private static class NonStringAnnotation {
        @RoutingKey
        Object foo;
    }

    private static class AnnotationOnStaticField {
        @RoutingKey
        private static final Object foo = "bar";
    }

    private interface AnnotationOnInterfaceMethod {
        @RoutingKey
        Object foo();
    }

    private interface AnnotationOnStaticInterfaceField {
        @RoutingKey
        Object foo = "bar";
    }

    private interface AnnotationOnStaticInterfaceMethod {
        @RoutingKey
        static Object foo() {
            return "bar";
        }
    }

    private interface AnnotationOnDefaultInterfaceMethod {
        @RoutingKey
        default Object foo() {
            return "bar";
        }
    }

    private static class AnnotationOnMethod {
        @RoutingKey
        private Object foo() {
            return "bar";
        }
    }

    private static class AnnotationOnStaticMethod {
        @RoutingKey
        private static Object foo() {
            return "bar";
        }
    }

    @RoutingKey(value = "foo")
    private static class AnnotationOnType {
    }

    private static class AnnotationOnExtendedField extends AnnotationOnField {
    }

    private static class AnnotationOnExtendedMethod extends AnnotationOnMethod {
    }

    private static class AnnotationOnWrongMethod {
        @RoutingKey
        private Object foo(Object bar) {
            return bar.toString();
        }
    }
}