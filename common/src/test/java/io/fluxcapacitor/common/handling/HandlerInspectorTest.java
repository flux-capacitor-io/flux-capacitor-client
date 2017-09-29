/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static java.util.function.Function.identity;
import static junit.framework.TestCase.*;

public class HandlerInspectorTest {

    private HandlerInvoker<Object> subject;
    private Foo foo;

    @Before
    public void setUp() throws Exception {
        subject = HandlerInspector.inspect(Foo.class, Handler.class, Collections.singletonList(p -> identity()));
        foo = new Foo();
    }

    @Test
    public void testCanHandle() {
        assertTrue(subject.canHandle(100L));
        assertTrue(subject.canHandle("bla"));
        assertTrue(subject.canHandle(50));
        assertTrue(subject.canHandle(4f));
        assertFalse(subject.canHandle('b'));
        assertFalse(subject.canHandle(foo));
    }

    @Test
    public void testInvoke() throws Exception {
        assertEquals(200L, subject.invoke(foo, 200L));
        assertEquals("a", subject.invoke(foo, "a"));
        assertEquals(15, subject.invoke(foo, 15));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvokeExceptionally() throws Exception {
        subject.invoke(foo, 3f);
    }

    @Test(expected = Exception.class)
    public void testInvokeUnknownType() throws Exception {
        subject.invoke(foo, 'b');
    }




    private static class Foo extends Bar implements SomeInterface {
        @Handler
        public Object handle(Long o) {
            return o;
        }

        @Handler
        @Override
        public Integer handle(Integer o) {
            return o;
        }

        @Handler
        public void handleAndThrowException(Float f) {
            throw new UnsupportedOperationException("yup");
        }
    }

    private static class Bar {
        @Handler
        public Object handle(String o) {
            return o;
        }
    }

    private interface SomeInterface {
        Integer handle(Integer o);
    }

}