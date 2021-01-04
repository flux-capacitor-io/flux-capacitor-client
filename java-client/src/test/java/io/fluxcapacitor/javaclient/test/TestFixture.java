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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;

@Slf4j
public class TestFixture extends AbstractTestFixture {

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
        if (handlers.isEmpty()) {
            return Registration.noOp();
        }
        FluxCapacitor fluxCapacitor = getFluxCapacitor();
        HandlerConfiguration<DeserializingMessage> handlerConfiguration = defaultHandlerConfiguration();
        Registration registration = fluxCapacitor.execute(f -> handlers.stream().flatMap(h -> Stream
                .of(fluxCapacitor.commandGateway().registerHandler(h, handlerConfiguration),
                    fluxCapacitor.queryGateway().registerHandler(h, handlerConfiguration),
                    fluxCapacitor.eventGateway().registerHandler(h, handlerConfiguration),
                    fluxCapacitor.eventStore().registerHandler(h, handlerConfiguration),
                    fluxCapacitor.errorGateway().registerHandler(h, handlerConfiguration)))
                .reduce(Registration::merge).orElse(Registration.noOp()));
        if (fluxCapacitor.scheduler() instanceof DefaultScheduler) {
            DefaultScheduler scheduler = (DefaultScheduler) fluxCapacitor.scheduler();
            registration = registration.merge(fluxCapacitor.execute(fc -> handlers.stream().flatMap(h -> Stream
                    .of(scheduler.registerHandler(h, handlerConfiguration)))
                    .reduce(Registration::merge).orElse(Registration.noOp())));
        } else {
            log.warn("Could not register local schedule handlers");
        }
        return registration;
    }

    @Override
    protected Then applyWhen(Callable<?> action, boolean catchAll) {
        getFluxCapacitor().execute(fc -> {
            checkTrackers();
            handleExpiredSchedulesLocally();
            return null;
        });
        return super.applyWhen(() -> {
            Object result = action.call();
            handleExpiredSchedulesLocally();
            return result;
        }, catchAll);
    }

    protected void handleExpiredSchedulesLocally() {
        SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
        if (schedulingClient instanceof InMemorySchedulingClient) {
            List<Schedule> expiredSchedules = ((InMemorySchedulingClient) schedulingClient)
                    .removeExpiredSchedules(getFluxCapacitor().serializer());
            if (getFluxCapacitor().scheduler() instanceof DefaultScheduler) {
                DefaultScheduler scheduler = (DefaultScheduler)  getFluxCapacitor().scheduler();
                expiredSchedules.forEach(s -> scheduler.handleLocally(
                        s, s.serialize(getFluxCapacitor().serializer())));
            }
        }
    }
}
