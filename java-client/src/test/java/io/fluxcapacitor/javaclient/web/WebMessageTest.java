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
        assertFalse(input.getHeaders("Set-Cookie").isEmpty());
    }

    @Test
    void testConvertComplexRequestViaBuilder() {
        HttpCookie cookie = new HttpCookie("foo-cookie", "bar-cookie");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        WebRequest input = WebRequest.builder().method(HttpRequestMethod.POST)
                .header("foo", "bar").header("foo", "bar2")
                .cookie(cookie).url("/test").payload("test").build();
        WebRequest converted = input.toBuilder().build();
        assertEquals((Object) input.getPayload(), converted.getPayload());
        assertEquals(input.getMetadata(), converted.getMetadata());
        assertFalse(input.getHeaders("Cookie").isEmpty());
    }
}