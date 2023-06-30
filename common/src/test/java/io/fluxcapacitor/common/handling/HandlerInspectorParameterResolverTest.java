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

import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HandlerInspectorParameterResolverTest {

    private final Foo foo = new Foo();
    private final Handler<Message> subject = HandlerInspector
            .createHandler(foo, Handle.class, Arrays.asList((p, methodAnnotation) -> {
                if (p.getDeclaringExecutable().getParameters()[0] == p) {
                    return Message::getPayload;
                }
                return null;
            }, (p, methodAnnotation) -> {
                if (p.getType().equals(Instant.class)) {
                    return m -> Instant.now();
                }
                return null;
            }));

    @Test
    public void testFindInvoker() {
        assertTrue(subject.findInvoker(new Message("payload")).isPresent());
        assertTrue(subject.findInvoker(new Message(0L)).isPresent());
        assertFalse(subject.findInvoker(new Message(0)).isPresent());
    }

    @Test
    public void testInvoke() {
        Message message = new Message("payload");
        assertEquals("payload", subject.findInvoker(message).orElseThrow().invoke());
        message = new Message(100L);
        assertEquals(100L, subject.findInvoker(message).orElseThrow().invoke());
    }

    private static class Foo {
        @Handle
        public Object handle(String o, Instant time) {
            assertNotNull(time);
            return o;
        }

        @Handle
        public Object handle(Long o, Instant time) {
            assertNotNull(time);
            return o;
        }
    }

    @Value
    private static class Message {
        Object payload;
    }

}