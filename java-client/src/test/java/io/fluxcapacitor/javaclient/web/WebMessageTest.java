package io.fluxcapacitor.javaclient.web;

import org.junit.jupiter.api.Test;

import java.net.HttpCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebMessageTest {

    @Test
    void testConvertComplexResponseViaBuilder() {
        HttpCookie cookie = new HttpCookie("foo-cookie", "bar-cookie");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        WebResponse input = WebResponse.builder().header("foo", "bar").header("foo", "bar2")
                .cookie(cookie).status(200).payload("test").build();
        WebResponse converted = input.toBuilder().build();
        assertEquals((Object) input.getPayload(), converted.getPayload());
        assertEquals(input.getMetadata(), converted.getMetadata());
        assertFalse(input.getHeaders().get("Set-Cookie").isEmpty());
    }

    @Test
    void testConvertComplexRequestViaBuilder() {
        HttpCookie cookie = new HttpCookie("foo-cookie", "bar-cookie");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        WebRequest input = WebRequest.builder().method(HttpRequestMethod.POST)
                .header("foo", "bar").header("foo", "bar2")
                .cookie(cookie).url("/test").payload("test").build();
        WebRequest converted = input.toBuilder().build();
        assertEquals((Object) input.getPayload(), converted.getPayload());
        assertEquals(input.getMetadata(), converted.getMetadata());
        assertFalse(input.getHeaders().get("Cookie").isEmpty());
    }
}