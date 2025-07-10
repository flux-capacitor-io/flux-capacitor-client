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
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.FixedUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.NonNull;
import org.junit.jupiter.api.Test;

public class HandleCustomTest {

    final TestFixture testFixture = TestFixture.create(new Handler());

    @Test
    void syncFixture() {
        testFixture
                .whenCustom("foo", "test")
                .expectResult("foo: test")
                .andThen()
                .whenCustom("bar", "test")
                .expectResult("bar: test")
                .andThen()
                .whenCustom("foo", 123)
                .expectResult("foo: 123")
                .andThen()
                .whenCustom("customEvent", "test")
                .expectNoResult()
                .expectEvents("custom: test")
                .expectCustom("other", "test");
    }

    @Test
    void withUser() {
        TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(new FixedUserProvider(() -> new MockUser("test"))), new Handler())
                .whenCustom("user", "test")
                .expectResult("user: test");
    }

    @Test
    void asyncFixture() {
        TestFixture.createAsync(new Handler())
                .whenCustom("foo", "test")
                .expectResult("foo: test")
                .andThen()
                .whenCustom("bar", "test")
                .expectResult("bar: test")
                .andThen()
                .whenCustom("foo", 123)
                .expectResult("foo: 123")
                .andThen()
                .whenCustom("customEvent", "test")
                .expectNoResult()
                .expectEvents("custom: test")
                .expectCustom("other", "test")
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
            FluxCapacitor.get().customGateway("other").sendAndForget(input);
        }

        @HandleCustom("user")
        @RequiresUser
        String handleFoo(String input, @NonNull User user) {
            return "user: " + input;
        }
    }

}
