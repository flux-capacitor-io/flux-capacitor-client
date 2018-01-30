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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handle;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.publishing.client.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.client.WebsocketTrackingClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class JavaClientRunner extends AbstractClientBenchmark {

    public static void main(final String[] args) {
        JavaClientRunner runner = new JavaClientRunner(100_000);
        runner.testCommands();
        System.exit(0);
    }

    private final GatewayClient gatewayClient;
    private final Handler<SerializedMessage> commandInvoker;

    public JavaClientRunner(int commandCount) {
        super(commandCount);

        gatewayClient = new WebsocketGatewayClient(
            ServiceUrlBuilder.producerUrl(MessageType.COMMAND, getClientProperties()));
        TrackingUtils.start("javaClientRunner/command",
                            new WebsocketTrackingClient(
                                  ServiceUrlBuilder.consumerUrl(MessageType.COMMAND, getClientProperties())),
                            this::handleCommands);
        commandInvoker =
                HandlerInspector.createHandler(this, Handle.class, Collections.singletonList(p -> m -> m));

        CountDownLatch commandsSentCountdown = new CountDownLatch(commandCount);
        gatewayClient.registerMonitor(m -> {
            commandsSentCountdown.countDown();
            if (commandsSentCountdown.getCount() == 0L) {
                log.info("Finished sending {} commands", commandCount);
            }
        });
    }

    @Override
    protected void doSendCommand(String payload) {
        gatewayClient.send(new SerializedMessage(new Data<>(payload.getBytes(), String.class.getName(), 0)));
    }

    @Handle
    public void handleCommand(SerializedMessage command) {
        getCommandCountDownLatch().countDown();
    }

    private void handleCommands(List<SerializedMessage> commands) {
        commands.forEach(m -> {
            try {
                commandInvoker.invoke(m);
            } catch (Exception e) {
                log.error("Failed to handle command", e);
                throw new IllegalStateException("Failed to handle command", e);
            }
        });
    }

}
