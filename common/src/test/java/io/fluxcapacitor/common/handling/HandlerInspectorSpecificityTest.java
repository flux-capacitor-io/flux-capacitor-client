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

import static junit.framework.TestCase.*;

public class HandlerInspectorSpecificityTest {

    private HandlerInvoker<Object> subject;
    private Foo foo;

    @Before
    public void setUp() throws Exception {
        subject = HandlerInspector.inspect(Foo.class, Handler.class, Collections.singletonList(p -> o -> o));
        foo = new Foo();
    }

    @Test
    public void testCanHandle() {
        assertTrue(subject.canHandle(new Message()));
        assertTrue(subject.canHandle(new MessageSuper()));
        assertFalse(subject.canHandle(foo));
    }

    @Test
    public void testInvoke() throws Exception {
        Message message = new Message();
        assertEquals(message, subject.invoke(foo, message));
    }

    private static class Foo {
        @Handler
        public void handle(MessageSuper o) {
            throw new AssertionError("Undesired invocation");
        }

        @Handler
        public Object handle(Message o) {
            return o;
        }

        @Handler
        public void h0(MessageSuper o) {
            throw new AssertionError("Undesired invocation");
        }

    }

    private static class Message extends MessageSuper {

    }

    private static class MessageSuper {

    }


}