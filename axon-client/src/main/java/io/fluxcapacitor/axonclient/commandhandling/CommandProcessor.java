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

package io.fluxcapacitor.axonclient.commandhandling;

import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.gateway.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.TrackingUtils;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;

import java.util.List;
import java.util.Optional;

@Slf4j
public class CommandProcessor {

    private final AxonMessageSerializer serializer;
    private final CommandBus localCommandBus;
    private final CommandCallback<Object, Object> commandCallback;
    private final TrackingClient trackingClient;
    private final String name;
    private final int threads;
    private volatile Registration registration;

    public CommandProcessor(AxonMessageSerializer serializer, CommandBus localCommandBus,
                            GatewayClient resultGatewayClient, String name,
                            TrackingClient trackingClient) {
        this(serializer, localCommandBus, new ReplyingCallback<>(resultGatewayClient, serializer), trackingClient,
             name, 1);
    }

    public CommandProcessor(AxonMessageSerializer serializer, CommandBus localCommandBus,
                            CommandCallback<Object, Object> commandCallback,
                            TrackingClient trackingClient, String name, int threads) {
        this.serializer = serializer;
        this.localCommandBus = localCommandBus;
        this.commandCallback = commandCallback;
        this.trackingClient = trackingClient;
        this.name = name;
        this.threads = threads;
    }

    public void start() {
        if (registration == null) {
            registration = TrackingUtils.start(name, threads, trackingClient, this::handle);
        }
    }

    public void shutDown() {
        Optional.ofNullable(registration).ifPresent(Registration::cancel);
        registration = null;
    }

    protected void handle(List<SerializedMessage> batch) {
        for (SerializedMessage message : batch) {
            CommandMessage<?> commandMessage = serializer.deserializeCommand(message);
            try {
                localCommandBus.dispatch(commandMessage, commandCallback);
            } catch (Exception e) {
                log.error("Failed to handle command {}. Reporting error.", commandMessage, e);
                commandCallback.onFailure(commandMessage, e);
            }
        }
    }
}
