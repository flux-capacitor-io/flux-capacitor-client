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

package io.fluxcapacitor.common.handling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerInspectorTest {

    private Handler<Object> subject;
    private Foo foo;

    @BeforeEach
    void setUp() {
        foo = new Foo();
        subject = HandlerInspector.createHandler(foo, Handle.class);
    }

    @Test
    void testFindInvoker() {
        assertTrue(subject.getInvoker(100L).isPresent());
        assertTrue(subject.getInvoker("bla").isPresent());
        assertTrue(subject.getInvoker(50).isPresent());
        assertTrue(subject.getInvoker(4f).isPresent());
        assertFalse(subject.getInvoker('b').isPresent());
        assertFalse(subject.getInvoker(foo).isPresent());
    }

    @Test
    void testHandleInPrivateMethod() {
        assertEquals(42, subject.getInvoker(true).orElseThrow().invoke());
    }

    @Test
    void testInvoke() {
        assertEquals(200L, subject.getInvoker(200L).orElseThrow().invoke());
        assertEquals("a", subject.getInvoker("a").orElseThrow().invoke());
        assertEquals(15, subject.getInvoker(15).orElseThrow().invoke());
    }

    @Test
    void testInvokeExceptionally() {
        assertThrows(UnsupportedOperationException.class, () -> subject.getInvoker(3f).orElseThrow().invoke());
    }

    @Test
    void testInvokeUnknownType() {
        assertThrows(Exception.class, () -> subject.getInvoker('b').orElseThrow().invoke());
    }

    @Test
    void testMetaAnnotationHandler() {
        subject = HandlerInspector.createHandler(new Meta(), Handle.class);
        assertEquals("a", subject.getInvoker("a").orElseThrow().invoke());
    }

    private static class Foo extends Bar implements SomeInterface {
        @Handle
        @Override
        public Object handle(Long o) {
            return o;
        }

        @Handle
        @Override
        public Integer handle(Integer o) {
            return o;
        }

        @Handle
        private Object handle(Boolean o) {
            return 42;
        }

        @Handle
        public void handleAndThrowException(Float f) {
            throw new UnsupportedOperationException("yup");
        }
    }

    private static class Bar {
        @Handle
        public Object handle(String o) {
            return o;
        }

        @Handle
        public Object handle(Long o) {
            return null;
        }

        @Handle
        public void handleAndThrowException(Integer ignored) {
            throw new UnsupportedOperationException("should not happen");
        }
    }

    private static class Meta {
        @MetaHandle
        public Object handle(String o) {
            return o;
        }
    }

    private interface SomeInterface {
        Integer handle(Integer o);
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface Handle {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Handle
    public @interface MetaHandle {
    }

}