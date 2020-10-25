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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class LowLevelJavaClientRunner extends AbstractClientBenchmark {

    public static void main(final String[] args) {
        LowLevelJavaClientRunner runner = new LowLevelJavaClientRunner(100_000);
        runner.testCommands();
        System.exit(0);
    }

    private final Client client;

    public LowLevelJavaClientRunner(int commandCount) {
        super(commandCount);

        client = WebSocketClient.newInstance(getClientProperties());
        DefaultTracker.start(this::handleCommands, ConsumerConfiguration.getDefault(MessageType.COMMAND).toBuilder()
                .prependApplicationName(false).name("javaClientRunner/command").build(), client
        );

        CountDownLatch commandsSentCountdown = new CountDownLatch(commandCount);
        commandGateway().registerMonitor(m -> {
            commandsSentCountdown.countDown();
            if (commandsSentCountdown.getCount() == 0L) {
                log.info("Finished sending {} commands", commandCount);
            }
        });
    }

    @Override
    protected void doSendCommand(String payload) {
        commandGateway().send(new SerializedMessage(
                new Data<>(payload.getBytes(), String.class.getName(), 0), Metadata.empty(),
                UUID.randomUUID().toString(), Clock.systemUTC().millis()));
    }

    protected GatewayClient commandGateway() {
        return client.getGatewayClient(MessageType.COMMAND);
    }

    private void handleCommands(List<SerializedMessage> commands) {
        commands.forEach(m -> {
            try {
                getCommandCountDownLatch().countDown();
            } catch (Exception e) {
                log.error("Failed to handle command", e);
                throw new IllegalStateException("Failed to handle command", e);
            }
        });
    }

}
