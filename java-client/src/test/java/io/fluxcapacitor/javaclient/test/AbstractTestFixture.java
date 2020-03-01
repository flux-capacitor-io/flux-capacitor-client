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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@Slf4j
public abstract class AbstractTestFixture implements Given, When {

    @Getter
    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;

    @Getter
    private Clock clock;
    private final Collection<Schedule> givenSchedules = new ArrayList<>();

    protected AbstractTestFixture(Function<FluxCapacitor, List<?>> handlerFactory) {
        this(DefaultFluxCapacitor.builder(), handlerFactory);
    }

    protected AbstractTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                                  Function<FluxCapacitor, List<?>> handlerFactory) {
        this(fluxCapacitorBuilder, handlerFactory, new TestClient());
    }

    protected AbstractTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                                  Function<FluxCapacitor, List<?>> handlerFactory, Client client) {
        Optional<TestUserProvider> userProvider =
                Optional.ofNullable(UserProvider.defaultUserSupplier).map(TestUserProvider::new);
        if (userProvider.isPresent()) {
            fluxCapacitorBuilder = fluxCapacitorBuilder.registerUserSupplier(userProvider.get());
        }
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder.disableShutdownHook().addDispatchInterceptor(interceptor)
                .build(client);
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        this.registration = registerHandlers(handlerFactory.apply(fluxCapacitor));
    }
    
    /*
        abstract
     */

    public abstract Registration registerHandlers(List<?> handlers);

    public abstract void deregisterHandlers(Registration registration);

    protected abstract Then createResultValidator(Object result);

    protected abstract void registerCommand(Message command);

    protected abstract void registerEvent(Message event);

    protected abstract void registerSchedule(Schedule schedule);

    protected abstract Object getDispatchResult(CompletableFuture<?> dispatchResult);

    protected abstract void handleExpiredSchedule(Schedule schedule);
    
    /*
        init
     */

    @Override
    public Given withClock(Clock clock) {
        this.clock = clock;
        SchedulingClient schedulingClient = getFluxCapacitor().client().getSchedulingClient();
        if (schedulingClient instanceof InMemorySchedulingClient) {
            ((InMemorySchedulingClient) schedulingClient).setClock(clock);
        } else {
            log.warn("Could not update clock of scheduling client. Timing tests may not work.");
        }
        return this;
    }
    
    /*
        given
     */

    @Override
    public When givenCommands(Object... commands) {
        return given(() -> getDispatchResult(CompletableFuture.allOf(flatten(commands).map(
                c -> fluxCapacitor.commandGateway().send(c)).toArray(CompletableFuture[]::new))));
    }

    @Override
    public When givenEvents(Object... events) {
        return given(() -> flatten(events).forEach(c -> fluxCapacitor.eventGateway().publish(c)));
    }

    @Override
    public When givenSchedules(Schedule... schedules) {
        return given(() -> Arrays.stream(schedules).forEach(this::handleGivenSchedule));
    }

    @Override
    public When given(Runnable condition) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            condition.run();
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute given", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }
    
    /*
        and given
     */

    @Override
    public When andGiven(Runnable runnable) {
        return given(runnable);
    }

    @Override
    public When andGivenCommands(Object... commands) {
        return givenCommands(commands);
    }

    @Override
    public When andGivenEvents(Object... events) {
        return givenEvents(events);
    }

    @Override
    public When andGivenSchedules(Schedule... schedules) {
        return givenSchedules(schedules);
    }

    @Override
    public When andThenTimeAdvancesTo(Instant instant) {
        return given(() -> advanceTimeTo(instant));
    }

    @Override
    public When andThenTimeElapses(Duration duration) {
        return given(() -> advanceTimeBy(duration));
    }
    
    /*
        when
     */

    @Override
    public Then whenCommand(Object command) {
        return applyWhen(() -> getDispatchResult(fluxCapacitor.commandGateway().send(interceptor.trace(command))),
                         false);
    }

    @Override
    public Then whenQuery(Object query) {
        return applyWhen(() -> getDispatchResult(fluxCapacitor.queryGateway().send(interceptor.trace(query))), false);
    }

    @Override
    public Then whenEvent(Object event) {
        return when(() -> fluxCapacitor.eventGateway().publish(interceptor.trace(event)), false);
    }

    @Override
    public Then when(Runnable task) {
        return when(task, true);
    }

    @Override
    @SneakyThrows
    public Then whenTimeElapses(Duration duration) {
        return when(() -> advanceTimeBy(duration), true);
    }

    @Override
    @SneakyThrows
    public Then whenTimeAdvancesTo(Instant instant) {
        return when(() -> advanceTimeTo(instant), true);
    }
    
    /*
        helper
     */

    protected void handleGivenSchedule(Schedule schedule) {
        givenSchedules.removeIf(s -> Objects.equals(schedule.getScheduleId(), s.getScheduleId()));
        if (!schedule.isExpired(getClock())) {
            givenSchedules.add(schedule);
        }
    }

    protected void advanceTimeBy(Duration duration) {
        advanceTimeTo(getClock().instant().plus(duration));
    }

    protected void advanceTimeTo(Instant instant) {
        withClock(Clock.fixed(instant, ZoneId.systemDefault()));
        new ArrayList<>(givenSchedules).stream().sorted(comparing(Schedule::getDeadline)).forEach(s -> {
            if (s.isExpired(getClock())) {
                handleExpiredSchedule(s);
            }
        });
    }

    protected Then when(Runnable action, boolean catchAll) {
        return applyWhen(() -> {
            action.run();
            return null;
        }, catchAll);
    }

    protected Then applyWhen(Supplier<Object> action, boolean catchAll) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            if (catchAll) {
                interceptor.catchAll();
            }
            Object result;
            try {
                result = action.get();
            } catch (Exception e) {
                result = e;
            }
            return createResultValidator(result);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    protected Stream<Object> flatten(Object... messages) {
        return Arrays.stream(messages).flatMap(c -> {
            if (c instanceof Collection<?>) {
                return ((Collection<?>) c).stream();
            }
            if (c.getClass().isArray()) {
                return Arrays.stream((Object[]) c);
            }
            return Stream.of(c);
        });
    }

    protected class GivenWhenThenInterceptor implements DispatchInterceptor {

        private static final String TAG = "givenWhenThen.tag";
        private static final String TAG_NAME = "$givenWhenThen.tagName";
        private static final String TRACE_NAME = "$givenWhenThen.trace";
        private volatile boolean catchAll;

        protected void catchAll() {
            this.catchAll = true;
        }

        protected Message trace(Object message) {
            catchAll = false;
            Message result =
                    message instanceof Message ? (Message) message : new Message(message, Metadata.empty());
            result.getMetadata().put(TAG_NAME, TAG);
            return result;
        }

        protected boolean isDescendantMetadata(Metadata messageMetadata) {
            return TAG.equals(messageMetadata.getOrDefault(TRACE_NAME, "").split(",")[0]);
        }

        @Override
        @SuppressWarnings("SuspiciousMethodCalls")
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                      MessageType messageType) {
            return message -> {
                message.getMetadata().putIfAbsent(TAG_NAME, UUID.randomUUID().toString());
                Optional.ofNullable(DeserializingMessage.getCurrent()).ifPresent(currentMessage -> {
                    if (currentMessage.getMetadata().containsKey(TRACE_NAME)) {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(
                                TRACE_NAME) + "," + currentMessage.getMetadata().get(TAG_NAME));
                    } else {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(TAG_NAME));
                    }
                });

                if (givenSchedules.contains(message)) {
                    return function.apply(message);
                }

                if (isDescendantMetadata(message.getMetadata()) || catchAll) {
                    switch (messageType) {
                        case COMMAND:
                            registerCommand(message);
                            break;
                        case EVENT:
                            registerEvent(message);
                            break;
                        case SCHEDULE:
                            registerSchedule((Schedule) message);
                            break;
                    }
                } else if (message instanceof Schedule) {
                    handleGivenSchedule((Schedule) message);
                }

                return function.apply(message);
            };
        }
    }
}
