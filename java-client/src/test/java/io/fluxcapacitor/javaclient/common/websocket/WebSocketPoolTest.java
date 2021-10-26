/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.websocket;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Builder;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketPoolTest {
    private final WebSocketPool subject = (WebSocketPool) WebSocketPool.builder(
            when(mock(Builder.class).buildAsync(any(), any())).thenAnswer(
                            i -> completedFuture(when(mock(WebSocket.class).isInputClosed()).thenReturn(false).getMock()))
                    .getMock()).sessionCount(3).build(URI.create("bla"), mock(WebSocket.Listener.class));

    @Test
    void testPoolCyclesSessions() {
        WebSocket first = subject.get();
        WebSocket second = subject.get();
        WebSocket third = subject.get();
        WebSocket fourth = subject.get();

        assertNotSame(first, second);
        assertNotSame(first, third);
        assertNotSame(second, third);
        assertSame(first, fourth);
    }
}