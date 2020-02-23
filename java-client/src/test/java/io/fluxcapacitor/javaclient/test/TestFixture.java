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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class TestFixture extends AbstractTestFixture {

    private final List<Message> events = new ArrayList<>();
    private final List<Message> commands = new ArrayList<>();
    private final List<Schedule> schedules = new ArrayList<>();

    public static TestFixture create(Object... handlers) {
        return new TestFixture(fc -> Arrays.asList(handlers));
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new TestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static TestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(handlersFactory);
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(fluxCapacitorBuilder, handlersFactory);
    }

    protected TestFixture(Function<FluxCapacitor, List<?>> handlersFactory) {
        super(handlersFactory);
    }

    protected TestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        super(fluxCapacitorBuilder, handlersFactory);
    }

    @Override
    public Registration registerHandlers(List<?> handlers) {
        return getFluxCapacitor().registerLocalHandlers(handlers)
                .merge(getFluxCapacitor().tracking(MessageType.SCHEDULE).start(getFluxCapacitor(), handlers));
    }

    @Override
    public void deregisterHandlers(Registration registration) {
        registration.cancel();
    }

    @Override
    protected Then createResultValidator(Object result) {
        return new ResultValidator(result, events, commands, schedules);
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
    protected void registerSchedule(Schedule schedule) {
        schedules.add(schedule);
    }

    @Override
    @SneakyThrows
    protected Object getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return dispatchResult.getNow(null);
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }
}
