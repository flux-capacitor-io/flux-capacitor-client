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

import io.fluxcapacitor.axonclient.commandhandling.result.ResultService;
import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FluxCapacitorCommandBus implements CommandBus {
    private final GatewayClient gatewayClient;
    private final ResultService resultService;
    private final AxonMessageSerializer serializer;
    private final RoutingStrategy routingStrategy;
    private final String clientId;
    private final SimpleCommandBus localCommandBus;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors =
            new CopyOnWriteArrayList<>();
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;

    public FluxCapacitorCommandBus(GatewayClient gatewayClient,
                                   ResultService resultService,
                                   AxonMessageSerializer serializer,
                                   RoutingStrategy routingStrategy, String clientId,
                                   SimpleCommandBus localCommandBus) {
        this(gatewayClient, resultService, serializer, routingStrategy, clientId,
             localCommandBus, NoOpMessageMonitor.INSTANCE);
    }

    public FluxCapacitorCommandBus(GatewayClient gatewayClient,
                                   ResultService resultService,
                                   AxonMessageSerializer serializer,
                                   RoutingStrategy routingStrategy, String clientId,
                                   SimpleCommandBus localCommandBus,
                                   MessageMonitor<? super CommandMessage<?>> messageMonitor) {
        this.gatewayClient = gatewayClient;
        this.resultService = resultService;
        this.serializer = serializer;
        this.routingStrategy = routingStrategy;
        this.clientId = clientId;
        this.localCommandBus = localCommandBus;
        this.messageMonitor = messageMonitor;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        if (NoOpMessageMonitor.INSTANCE.equals(messageMonitor)) {
            send(intercept(command));
        } else {
            dispatch(command, null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        CommandMessage<? extends C> intercepted = intercept(command);
        MonitorAwareCallback<? super C, R>
                monitorAwareCallback = new MonitorAwareCallback<>(callback, messageMonitor.onMessageIngested(intercepted));
        resultService.awaitResult(command.getIdentifier()).handle((result, e) -> {
            if (e != null) {
                monitorAwareCallback.onFailure(intercepted, e);
            } else {
                monitorAwareCallback.onSuccess(intercepted, (R) result);
            }
            return null;
        });
        send(command.andMetaData(Collections.singletonMap("sender", clientId)));
    }

    private void send(CommandMessage<?> command) {
        gatewayClient.send(toFluxCapacitorMessage(command));
    }

    private SerializedMessage toFluxCapacitorMessage(CommandMessage<?> commandMessage) {
        SerializedMessage
                result = new SerializedMessage(new Data<>(serializer.serializeCommand(commandMessage), commandMessage.getCommandName(), 0), Metadata.empty());
        String routingKey = routingStrategy.getRoutingKey(commandMessage);
        result.setSegment(ConsistentHashing.computeSegment(routingKey));
        return result;
    }

    @SuppressWarnings("unchecked")
    private <C> CommandMessage<? extends C> intercept(CommandMessage<C> command) {
        CommandMessage<? extends C> interceptedCommand = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            interceptedCommand = (CommandMessage<? extends C>) interceptor.handle(interceptedCommand);
        }
        return interceptedCommand;
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return localCommandBus.subscribe(commandName, handler);
    }

    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return localCommandBus.registerHandlerInterceptor(handlerInterceptor);
    }

    public void setRollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
        localCommandBus.setRollbackConfiguration(rollbackConfiguration);
    }
}
