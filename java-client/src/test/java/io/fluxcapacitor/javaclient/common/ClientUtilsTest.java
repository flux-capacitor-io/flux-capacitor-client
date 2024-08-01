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

package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.MemoizingSupplier;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientUtilsTest {

    @Test
    void handleOnlyTracking() throws Exception {
        assertTrue(
                ClientUtils.isTrackingHandler(Handler.class, Handler.class.getDeclaredMethod("handle", String.class)));
        assertFalse(ClientUtils.isLocalHandler(Handler.class, Handler.class.getDeclaredMethod("handle", String.class)));
    }

    @Test
    void handleOnlyLocal() throws Exception {
        assertTrue(ClientUtils.isLocalHandler(Handler.class,
                                              Handler.class.getDeclaredMethod("handleOnlyLocal", int.class)));
        assertFalse(ClientUtils.isTrackingHandler(Handler.class,
                                                  Handler.class.getDeclaredMethod("handleOnlyLocal", int.class)));
    }

    @Test
    void handleLocalAndExternal() throws Exception {
        assertTrue(ClientUtils.isLocalHandler(Handler.class,
                                              Handler.class.getDeclaredMethod("handleLocalOrExternal", double.class)));
        assertTrue(ClientUtils.isTrackingHandler(Handler.class, Handler.class.getDeclaredMethod("handleLocalOrExternal",
                                                                                                double.class)));
    }

    @Nested
    class MemoizeTests {
        final AtomicInteger counter = new AtomicInteger();
        final MemoizingSupplier<Integer> supplier =
                ClientUtils.memoize(counter::incrementAndGet, Duration.ofSeconds(10));

        private final TestFixture testFixture = TestFixture.create(new Object() {
            @HandleCommand
            int handle() {
                return supplier.get();
            }
        });

        @Test
        void memoizeWithLifespan_refreshValueAfterLifespan() {
            testFixture.givenCommands(new Object()).givenElapsedTime(Duration.ofSeconds(15))
                    .whenCommand(new Object()).expectResult(2);
        }

        @Test
        void memoizeWithLifespan_dontRefreshBeforeLifespan() {
            testFixture.givenCommands(new Object()).givenElapsedTime(Duration.ofSeconds(5))
                    .whenCommand(new Object()).expectResult(1);
        }

        @Test
        void memoizeWithLifespan_cleared() {
            testFixture.givenCommands(new Object()).givenElapsedTime(Duration.ofSeconds(5))
                    .given(fc -> supplier.clear())
                    .whenCommand(new Object()).expectResult(2);
        }
    }

    private static class Handler {
        @HandleCommand
        String handle(String command) {
            return command;
        }

        @HandleCommand
        @LocalHandler
        int handleOnlyLocal(int command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(allowExternalMessages = true)
        double handleLocalOrExternal(double command) {
            return command;
        }
    }
}