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

package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static io.undertow.Handlers.path;

@Slf4j
public class ProxyServer {
    public static void main(final String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught error", e));
        int port = Integer.parseInt(Optional.ofNullable(System.getenv("PROXY_PORT")).orElse("80"));
        Client client = Optional.ofNullable(System.getenv("FLUX_URL")).<Client>map(url -> WebSocketClient.newInstance(
                WebSocketClient.ClientConfig.builder().name("$proxy").serviceBaseUrl(url).build()))
                .orElseThrow(() -> new IllegalStateException("FLUX_URL environment variable is not set"));
        start(port, new ProxyRequestHandler(client));
    }

    public static Registration start(int port, ProxyRequestHandler proxyHandler) {
        Undertow server = Undertow.builder().addHttpListener(port, "0.0.0.0")
                .setHandler(path().addPrefixPath("/", proxyHandler))
                .build();
        server.start();
        log.info("Flux Capacitor proxy server running on port {}", port);
        return () -> {
            proxyHandler.close();
            server.stop();
        };
    }
}
