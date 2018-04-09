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


import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerInspectorSpecificityTest {

    private Foo foo = new Foo();
    private Handler<Object> subject =
            HandlerInspector.createHandler(foo, Handle.class, Collections.singletonList(p -> o -> o));

    @Test
    void testCanHandle() {
        assertTrue(subject.canHandle(new Message()));
        assertTrue(subject.canHandle(new MessageSuper()));
        assertFalse(subject.canHandle(foo));
    }

    @Test
    void testInvoke() {
        Message message = new Message();
        assertEquals(message, subject.invoke(message));
    }

    private static class Foo {
        @Handle
        public void handle(MessageSuper o) {
            throw new AssertionError("Undesired invocation");
        }

        @Handle
        public Object handle(Message o) {
            return o;
        }

        @Handle
        public void h0(MessageSuper o) {
            throw new AssertionError("Undesired invocation");
        }

    }

    private static class Message extends MessageSuper {

    }

    private static class MessageSuper {

    }


}