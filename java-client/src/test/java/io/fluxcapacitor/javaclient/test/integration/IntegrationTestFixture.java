/*
 * Copyright (c) 2016-2018 Flux Capacitor. 
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

package io.fluxcapacitor.javaclient.test.integration;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import io.fluxcapacitor.javaclient.test.Then;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class IntegrationTestFixture extends AbstractTestFixture {

    private final BlockingQueue<Message> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> commands = new LinkedBlockingQueue<>();

    public static IntegrationTestFixture create(Object... handlers) {
        return new IntegrationTestFixture(DefaultFluxCapacitor.builder(), fc -> Arrays.asList(handlers));
    }

    public static IntegrationTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new IntegrationTestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static IntegrationTestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return new IntegrationTestFixture(DefaultFluxCapacitor.builder(), handlersFactory);
    }

    public static IntegrationTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        return new IntegrationTestFixture(fluxCapacitorBuilder, handlersFactory);
    }

    protected IntegrationTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        super(fluxCapacitorBuilder, handlersFactory);
    }

    @Override
    protected Registration registerHandlers(List<?> handlers, FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.startTracking(handlers);
    }

    @Override
    protected Then createResultValidator(Object result) {
        return new AsyncResultValidator(result, events, commands);
    }

    @Override
    protected void registerCommand(Message command) {
        commands.add(command);
    }

    @Override
    protected void registerEvent(Message event) {
        events.add(event);
    }

    @Override
    @SneakyThrows
    protected Object getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return dispatchResult.get(1L, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
