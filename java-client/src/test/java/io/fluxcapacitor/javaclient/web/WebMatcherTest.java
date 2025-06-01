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

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.web.internal.WebUtilsInternal;
import io.jooby.Context;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebMatcherTest {

    @Test
    void name() {
        var router = WebUtilsInternal.router();
        router.get("/abc/{userId}", ctx -> "regex");
        router.get("/abc/henk", ctx -> "henk");

        var context = mock(Context.class);
        when(context.getMethod()).thenReturn("GET");
        when(context.getRequestPath()).thenReturn("/abc/piet");

        var match = router.match(context);

        assertTrue(match.matches());
        assertEquals("regex", match.execute(context));
    }

    @Test
    void getHandler() {
        var handler = new GetHandler();
        var matcher = WebHandlerMatcher.create(handler.getClass(), Collections.emptyList(), HandlerConfiguration.
                <DeserializingMessage>builder().methodAnnotation(HandleWeb.class).build());

        WebRequest webRequest = WebRequest.builder().method(HttpRequestMethod.GET).url("/abc/henk").build();
        var m = new DeserializingMessage(webRequest, WEBREQUEST, mock(Serializer.class));



        assertTrue(matcher.canHandle(m));
        assertEquals("henk", matcher.getInvoker(handler, m).orElseThrow().invoke());
    }

    static class GetHandler {
        @HandleGet("/abc/{userId}")
        String handleAny() {
            return "any";
        }

        @HandleGet("/abc/henk")
        String handleHenk() {
            return "henk";
        }

        @HandleSocketOpen("/abc/henk")
        String handleWsHenk() {
            return "henk";
        }
    }
}