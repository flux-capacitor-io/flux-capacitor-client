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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.web.HandleGet;
import io.fluxcapacitor.javaclient.web.HandlePost;
import io.fluxcapacitor.javaclient.web.HandleWeb;
import io.fluxcapacitor.javaclient.web.WebRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.PUT;

public class GivenWhenThenWebTest {

    @Nested
    class WhenTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
                    .expectResult("payload");
        }

        private class Handler {
            @HandleWeb(value = "/get", method = GET)
            String get() {
                return "get";
            }

            @HandleWeb(value = "/string", method = POST)
            String post(String body) {
                return body;
            }
        }
    }

    @Nested
    class GivenTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testMultiGet() {
            testFixture.givenPost("/string", "foo")
                    .whenGet("get").expectResult("foo")
                    .andThen()
                    .whenGet("/get2").expectResult("foo")
                    .andThen()
                    .whenGet("/get3").expectResult("foo");
        }

        @Test
        void testMultiMethod() {
            testFixture.givenPost("/multi", "foo")
                    .whenGet("get").expectResult("foo")
                    .andThen()
                    .givenPut("/multi", "foo2")
                    .whenGet("/get2").expectResult("foo2")
                    .andThen()
                    .whenGet("/get3").expectResult("foo2");
        }

        private static class Handler {
            private String posted;

            @HandleGet("get")
            String get() {
                return posted;
            }

            @HandleGet(value = {"get2", "get3"})
            String getOther() {
                return posted;
            }

            @HandlePost("/string")
            void post(String body) {
                this.posted = body;
            }

            @HandleWeb(value = "/multi", method = {POST, PUT})
            void multi(String body) {
                this.posted = body;
            }
        }
    }

    @Nested
    class AsyncTest {
        private final TestFixture testFixture = TestFixture.createAsync(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/get").build())
                    .expectResult("get".getBytes());
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
                    .expectResult("payload".getBytes());
        }

        private class Handler {
            @HandleGet("get")
            String get() {
                return "get";
            }

            @HandlePost("/string")
            String post(String body) {
                return body;
            }
        }
    }
}
