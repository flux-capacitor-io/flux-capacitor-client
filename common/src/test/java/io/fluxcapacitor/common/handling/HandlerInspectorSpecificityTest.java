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


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerInspectorSpecificityTest {

    private final Foo foo = new Foo();
    private final Handler<Object> subject =
            HandlerInspector.createHandler(foo, Handle.class);

    @Test
    void testFindInvoker() {
        assertTrue(subject.getInvoker(new Message()).isPresent());
        assertTrue(subject.getInvoker(new MessageSuper()).isPresent());
        assertFalse(subject.getInvoker(foo).isPresent());
    }

    @Test
    void testInvoke() {
        Message message = new Message();
        assertEquals(message, subject.getInvoker(message).orElseThrow().invoke());
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