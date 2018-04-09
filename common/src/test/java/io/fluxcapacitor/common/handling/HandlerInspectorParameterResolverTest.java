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

import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HandlerInspectorParameterResolverTest {

    private Foo foo = new Foo();
    private Handler<Message> subject = HandlerInspector
            .createHandler(foo, Handle.class, Arrays.asList(p -> {
                if (p.getDeclaringExecutable().getParameters()[0] == p) {
                    return Message::getPayload;
                }
                return null;
            }, p -> {
                if (p.getType().equals(Instant.class)) {
                    return m -> Instant.now();
                }
                return null;
            }));

    @Test
    public void testCanHandle() {
        assertTrue(subject.canHandle(new Message("payload")));
        assertTrue(subject.canHandle(new Message(0L)));
        assertFalse(subject.canHandle(new Message(0)));
    }

    @Test
    public void testInvoke() {
        Message message = new Message("payload");
        assertEquals("payload", subject.invoke(message));
        message = new Message(100L);
        assertEquals(100L, subject.invoke(message));
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