/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

@Slf4j
public class HighLevelJavaClientRunner extends AbstractClientBenchmark {

    public static void main(final String[] args) {
        HighLevelJavaClientRunner runner = new HighLevelJavaClientRunner(100_000);
        runner.testCommands();
        System.exit(0);
    }

    private final FluxCapacitor fluxCapacitor;

    public HighLevelJavaClientRunner(int commandCount) {
        super(commandCount);
        fluxCapacitor = DefaultFluxCapacitor.builder().build(WebSocketClient.newInstance(getClientProperties()));
        fluxCapacitor.startTracking(this);
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
