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

package io.fluxcapacitor.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklogTest {

    private Backlog.BatchConsumer<Object> consumer = consumables -> Awaitable.ready();
    private Backlog<Object> subject = new Backlog<>(consumer);

    @Test
    void noMoreItemsAcceptedAfterShutdown() {
        subject.add(new Object());
        subject.shutdown();
        assertThrows(Exception.class, () -> subject.add(new Object()));
    }

    @Test
    void backlogWaitsOnExistingConsumerTaskWhenShutdown() {
        AtomicBoolean done = new AtomicBoolean();
        consumer = consumables -> {
            Thread.sleep(100);
            done.set(true);
            return Awaitable.ready();
        };
        subject = new Backlog<>(consumer);
        subject.add(new Object());
        subject.shutdown();
        assertTrue(done.get());
    }
}