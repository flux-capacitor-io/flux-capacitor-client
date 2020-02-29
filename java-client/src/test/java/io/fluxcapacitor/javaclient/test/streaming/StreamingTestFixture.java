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

package io.fluxcapacitor.javaclient.test.streaming;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import io.fluxcapacitor.javaclient.test.Then;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.SECONDS;

public class StreamingTestFixture extends AbstractTestFixture {

    private final BlockingQueue<Message> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> commands = new LinkedBlockingQueue<>();
    private final BlockingQueue<Schedule> schedules = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService deregistrationService = Executors.newSingleThreadScheduledExecutor();

    public static StreamingTestFixture create(Object... handlers) {
        return new StreamingTestFixture(fc -> Arrays.asList(handlers));
    }

    public static StreamingTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new StreamingTestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static StreamingTestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return new StreamingTestFixture(handlersFactory);
    }

    public static StreamingTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        return new StreamingTestFixture(fluxCapacitorBuilder, handlersFactory);
    }

    protected StreamingTestFixture(Function<FluxCapacitor, List<?>> handlersFactory) {
        super(handlersFactory);
    }

    protected StreamingTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        super(fluxCapacitorBuilder, handlersFactory);
    }

    @Override
    public Registration registerHandlers(List<?> handlers) {
        return getFluxCapacitor().startTracking(handlers);
    }

    @Override
    public void deregisterHandlers(Registration registration) {
        deregistrationService.schedule(registration::cancel, 1L, SECONDS);
    }

    @Override
    protected Then createResultValidator(Object result) {
        return new AsyncResultValidator(result, events, commands, schedules);
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
            return dispatchResult.get(1L, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Override
    protected void handleGivenSchedule(Schedule schedule) {
        super.handleGivenSchedule(schedule);
        getFluxCapacitor().scheduler().schedule(schedule);
    }

    @Override
    protected void handleExpiredSchedule(Schedule schedule) {
        //no op - has already been scheduled
    }
}
