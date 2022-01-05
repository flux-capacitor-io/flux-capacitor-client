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

public class GivenWhenThenWebTest {

    @Nested
    class WhenTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/string").payload("payload").build())
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
        void testGivenPostWhenGet() {
            String payload = "testPayload";
            testFixture.givenWebRequest(WebRequest.builder().method(POST).path("/string").payload(payload).build())
                    .whenWebRequest(WebRequest.builder().method(GET).path("/get").build()).expectResult(payload);
        }

        private class Handler {
            private String posted;

            @HandleGet("get")
            String get() {
                return posted;
            }

            @HandlePost("/string")
            void post(String body) {
                this.posted = body;
            }
        }
    }

    @Nested
    class AsyncTest {
        private final TestFixture testFixture = TestFixture.createAsync(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/string").payload("payload").build())
                    .expectResult("payload");
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
