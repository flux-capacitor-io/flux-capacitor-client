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

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
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
        Registration registration = getFluxCapacitor().registerLocalHandlers(handlers);
        if (getFluxCapacitor().scheduler() instanceof DefaultScheduler) {
            DefaultScheduler scheduler = (DefaultScheduler) getFluxCapacitor().scheduler();
            registration = registration.merge(getFluxCapacitor().execute(fc -> handlers.stream().flatMap(h -> Stream
                    .of(scheduler.registerLocalHandler(h))).reduce(Registration::merge).orElse(Registration.noOp())));
        } else {
            log.warn("Could not register local schedule handlers");
        }
        return registration;
    }

    @Override
    public void deregisterHandlers(Registration registration) {
        registration.cancel();
    }

    @Override
    protected Then createResultValidator(Object result) {
        return new ResultValidator(getFluxCapacitor(), result, events, commands, schedules);
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

    @Override
    protected void handleExpiredSchedule(Schedule schedule) {
        getFluxCapacitor().scheduler().schedule(schedule);
    }
}
