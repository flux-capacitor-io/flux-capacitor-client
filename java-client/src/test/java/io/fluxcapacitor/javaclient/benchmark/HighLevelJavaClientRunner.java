/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.benchmark;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class HighLevelJavaClientRunner extends AbstractClientBenchmark {

    public static void main(final String[] args) {
        HighLevelJavaClientRunner runner = new HighLevelJavaClientRunner(
                1_000, WebSocketClient.ClientConfig.builder()
                .name("benchmark-" + UUID.randomUUID())
                .projectId("benchmark")
                .serviceBaseUrl("https://flux-capacitor.sloppy.zone")
                .build());
        runner.testCommands();
        System.exit(0);
    }

    private final FluxCapacitor fluxCapacitor;

    public HighLevelJavaClientRunner(int commandCount) {
        super(commandCount);
        fluxCapacitor = DefaultFluxCapacitor.builder().build(WebSocketClient.newInstance(getClientConfig()));
        fluxCapacitor.registerHandlers(this);
    }

    public HighLevelJavaClientRunner(int commandCount, WebSocketClient.ClientConfig clientConfig) {
        super(commandCount, clientConfig);
        fluxCapacitor = DefaultFluxCapacitor.builder().build(WebSocketClient.newInstance(clientConfig));
        fluxCapacitor.registerHandlers(this);
    }

    @Override
    protected void doSendCommand(String payload) {
        fluxCapacitor.commandGateway().sendAndForget(payload);
    }

    @HandleCommand(passive = true)
    public void handleCommand(String command) {
        getCommandCountDownLatch().countDown();
    }

}
