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
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SupportsTimeTravel;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.SneakyThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class AbstractTestFixture implements Given, When {

    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;
    private volatile Clock clock;

    protected AbstractTestFixture(Function<FluxCapacitor, List<?>> handlerFactory) {
        this(DefaultFluxCapacitor.builder(), handlerFactory);
    }

    protected AbstractTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder,
                                  Function<FluxCapacitor, List<?>> handlerFactory) {
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder
                .registerUserSupplier(Optional.ofNullable(UserProvider.defaultUserSupplier).map(
                        TestUserProvider::new).orElse(null))
                .disableShutdownHook().addDispatchInterceptor(interceptor).build(new TestClient());
        this.registration = registerHandlers(handlerFactory.apply(fluxCapacitor));
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
    }

    public abstract Registration registerHandlers(List<?> handlers);

    public abstract void deregisterHandlers(Registration registration);

    protected abstract Then createResultValidator(Object result);

    protected abstract void registerCommand(Message command);

    protected abstract void registerEvent(Message event);

    protected abstract void registerSchedule(Schedule schedule);

    protected abstract Object getDispatchResult(CompletableFuture<?> dispatchResult);

    @Override
    public Given withClock(Clock clock) {
        getSchedulingClient().useClock(this.clock = clock);
        return this;
    }

    @Override
    public When givenCommands(Object... commands) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            getDispatchResult(CompletableFuture.allOf(flatten(commands).map(
                    c -> fluxCapacitor.commandGateway().send(c)).toArray(CompletableFuture[]::new)));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenCommands", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When givenEvents(Object... events) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            flatten(events).forEach(c -> fluxCapacitor.eventGateway().publish(c));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenEvents", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When given(Runnable condition) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            condition.run();
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute given condition", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When givenSchedules(Schedule... schedules) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Arrays.stream(schedules).forEach(s -> fluxCapacitor.scheduler().schedule(s));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenEvents", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Clock getClock() {
        return clock;
    }

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
    public Then whenCommand(Object command) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = getDispatchResult(fluxCapacitor.commandGateway().send(interceptor.trace(command)));
            } catch (Exception e) {
                result = e;
            }
            return createResultValidator(result);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenEvent(Object event) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            fluxCapacitor.eventGateway().publish(interceptor.trace(event));
            return createResultValidator(null);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenQuery(Object query) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = getDispatchResult(fluxCapacitor.queryGateway().send(interceptor.trace(query)));
            } catch (Exception e) {
                result = e;
            }
            return createResultValidator(result);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then when(Runnable task) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            interceptor.catchAll();
            task.run();
            return createResultValidator(null);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    @SneakyThrows
    public Then whenTimeElapses(Duration duration) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            interceptor.catchAll();
            getSchedulingClient().advanceTimeBy(duration);
            return createResultValidator(null);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    @SneakyThrows
    public Then whenTimeAdvancesTo(Instant instant) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            interceptor.catchAll();
            getSchedulingClient().advanceTimeTo(instant);
            return createResultValidator(null);
        } finally {
            deregisterHandlers(registration);
            FluxCapacitor.instance.remove();
        }
    }

    protected SupportsTimeTravel getSchedulingClient() {
        SchedulingClient schedulingClient = fluxCapacitor.client().getSchedulingClient();
        if (!(schedulingClient instanceof SupportsTimeTravel)) {
            throw new UnsupportedOperationException("Client does not support time jumps");
        }
        return (SupportsTimeTravel) schedulingClient;
    }

    public FluxCapacitor getFluxCapacitor() {
        return fluxCapacitor;
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
            return TAG.equals(getTrace(messageMetadata).get(0));
        }

        protected List<String> getTrace(Metadata messageMetadata) {
            return Arrays.asList(messageMetadata.getOrDefault(TRACE_NAME, "").split(","));
        }

        @Override
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                      MessageType messageType) {
            return message -> {
                String tag = UUID.randomUUID().toString();
                message.getMetadata().putIfAbsent(TAG_NAME, tag);
                Optional.ofNullable(DeserializingMessage.getCurrent()).ifPresent(currentMessage -> {
                    if (currentMessage.getMetadata().containsKey(TRACE_NAME)) {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(
                                TRACE_NAME) + "," + currentMessage.getMetadata().get(TAG_NAME));
                    } else {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(TAG_NAME));
                    }
                });
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
                }
                return function.apply(message);
            };
        }
    }
}
