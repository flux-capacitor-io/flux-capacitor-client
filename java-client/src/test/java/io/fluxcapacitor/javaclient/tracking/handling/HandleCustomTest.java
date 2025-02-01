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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

public class HandleCustomTest {

    final TestFixture testFixture = TestFixture.create(new Handler());

    @Test
    void syncFixture() {
        testFixture.whenApplying(fc -> fc.customGateway("foo").sendAndWait("test"))
                .expectResult("foo: test")
                .andThen()
                .whenApplying(fc -> fc.customGateway("bar").sendAndWait("test"))
                .expectResult("bar: test")
                .andThen()
                .whenApplying(fc -> fc.customGateway("foo").sendAndWait(123))
                .expectResult("foo: 123")
                .andThen()
                .whenExecuting(fc -> fc.customGateway("customEvent").sendAndForget("test"))
                .expectNoResult()
                .expectEvents("custom: test");
    }

    @Test
    void asyncFixture() {
        TestFixture.createAsync(new Handler())
                .whenApplying(fc -> fc.customGateway("foo").sendAndWait("test"))
                .expectResult("foo: test")
                .andThen()
                .whenApplying(fc -> fc.customGateway("bar").sendAndWait("test"))
                .expectResult("bar: test")
                .andThen()
                .whenApplying(fc -> fc.customGateway("foo").sendAndWait(123))
                .expectResult("foo: 123")
                .andThen()
                .whenExecuting(fc -> fc.customGateway("customEvent").sendAndForget("test"))
                .expectNoResult()
                .expectEvents("custom: test")
        ;
    }

    static class Handler {
        @HandleCustom("foo")
        String handleFoo(String input) {
            return "foo: " + input;
        }

        @HandleCustom("foo")
        String handleFooObject(Object input) {
            return "foo: " + input;
        }

        @HandleCustom("bar")
        String handleBar(String input) {
            return "bar: " + input;
        }

        @HandleCustom("customEvent")
        void handleCustomEvent(String input) {
            FluxCapacitor.publishEvent("custom: " + input);
        }
    }

}
