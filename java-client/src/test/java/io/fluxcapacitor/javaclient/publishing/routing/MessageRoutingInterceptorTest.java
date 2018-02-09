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
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.reflection.PropertyAccessException;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MessageRoutingInterceptorTest {

    private MessageRoutingInterceptor subject = new MessageRoutingInterceptor();
    private Function<Message, SerializedMessage> invocation = m -> new SerializedMessage(new Data<>("test".getBytes(), "test", 0));
    private int expectedHash = ConsistentHashing.computeSegment("bar");

    @Test
    public void testNoSegmentWithoutAnnotation() {
        SerializedMessage result = subject.interceptDispatch(invocation).apply(new Message(new Object(), MessageType.EVENT));
        assertNull(result.getSegment());
    }

    @Test
    public void testFieldAnnotation() {
        testInvocation(new AnnotationOnField());
    }

    @Test
    public void testMethodAnnotation() {
        testInvocation(new AnnotationOnMethod());
    }

    @Test
    public void testAnnotationOnNestedField() {
        testInvocation(new AnnotationOnNestedField());
    }

    @Test
    public void testAnnotationOnNestedMethod() {
        testInvocation(new AnnotationOnNestedMethod());
    }

    @Test(expected = PropertyAccessException.class)
    public void testAnnotationOnMethodWithParametersFails() {
        testInvocation(new AnnotationOnWrongMethod());
    }

    private void testInvocation(Object payload) {
        SerializedMessage result = subject.interceptDispatch(invocation).apply(new Message(payload, MessageType.EVENT));
        assertEquals(expectedHash, (int) result.getSegment());
    }

    private static class AnnotationOnField {
        @RoutingKey
        private Object foo = "bar";
    }

    private static class AnnotationOnMethod {
        @RoutingKey
        private Object foo() {
            return "bar";
        }
    }

    private static class AnnotationOnNestedField extends AnnotationOnField {
    }

    private static class AnnotationOnNestedMethod extends AnnotationOnMethod {
    }

    private static class AnnotationOnWrongMethod {
        @RoutingKey
        private Object foo(Object bar) {
            return bar.toString();
        }
    }
}