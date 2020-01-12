/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageRoutingInterceptorTest {

    private MessageRoutingInterceptor subject = new MessageRoutingInterceptor();
    private Function<Message, SerializedMessage> invocation = m -> new SerializedMessage(
            new Data<>("test".getBytes(), "test", 0), Metadata.empty(), "someId", 
            Clock.systemUTC().millis());
    private int expectedHash = ConsistentHashing.computeSegment("bar");

    @Test
    void testNoSegmentWithoutAnnotation() {
        SerializedMessage result =
                subject.interceptDispatch(invocation, EVENT).apply(new Message(new Object()));
        assertNull(result.getSegment());
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
    void testStaticInterfaceFieldAnnotation() {
        testInvocation(new AnnotationOnStaticInterfaceField() {});
    }

    @Test
    void testStaticInterfaceMethodAnnotation() {
        testInvocation(new AnnotationOnStaticInterfaceMethod() {});
    }

    @Test
    void testDefaultInterfaceMethodAnnotation() {
        testInvocation(new AnnotationOnDefaultInterfaceMethod() {});
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
        assertThrows(Exception.class, () -> testInvocation(new AnnotationOnWrongMethod()));
    }

    private void testInvocation(Object payload) {
        SerializedMessage result = subject.interceptDispatch(invocation, EVENT).apply(new Message(payload));
        assertEquals(expectedHash, (int) result.getSegment());
    }

    private static class AnnotationOnField {
        @RoutingKey
        private Object foo = "bar";
    }

    @Value
    private static class NonStringAnnotation {
        @RoutingKey
        Object foo;
    }

    private static class AnnotationOnStaticField {
        @RoutingKey
        private static Object foo = "bar";
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