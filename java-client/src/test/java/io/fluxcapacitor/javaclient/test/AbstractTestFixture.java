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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.ClientUtils.isLocalHandlerMethod;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
public abstract class AbstractTestFixture implements Given, When {

    @Getter
    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;

    private final Map<Tracker, List<Message>> trackers = new ConcurrentHashMap<>();
    private final List<Message> events = new ArrayList<>();
    private final List<Message> commands = new ArrayList<>();
    private final List<Schedule> schedules = new ArrayList<>();

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
                .addBatchInterceptor(interceptor).addHandlerInterceptor(interceptor).build(client);
        withClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        this.registration = registerHandlers(handlerFactory.apply(fluxCapacitor));
    }

    /*
        abstract
     */

    public abstract Registration registerHandlers(List<?> handlers);

    /*
        clock
     */

    @Override
    public Given withClock(Clock clock) {
        return getFluxCapacitor().execute(fc -> {
            fc.withClock(clock);
            SchedulingClient schedulingClient = fc.client().getSchedulingClient();
            if (schedulingClient instanceof InMemorySchedulingClient) {
                ((InMemorySchedulingClient) schedulingClient).setClock(clock);
            } else {
                log.warn("Could not update clock of scheduling client. Timing tests may not work.");
            }
            return this;
        });
    }

    public Clock getClock() {
        return getFluxCapacitor().clock();
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
    public When givenDomainEvents(String aggregateId, Object... events) {
        return given(() -> {
            List<Message> eventList = flatten(events).map(e -> {
                Message m = e instanceof Message ? (Message) e : new Message(e);
                return m.withMetadata(m.getMetadata().with(Aggregate.AGGREGATE_ID_METADATA_KEY, aggregateId));
            }).collect(toList());
            for (int i = 0; i < eventList.size(); i++) {
                Message event = eventList.get(i);
                if (event.getPayload() instanceof Data<?>) {
                    Data<?> eventData = event.getPayload();
                    Data<byte[]> eventBytes = fluxCapacitor.serializer().serialize(eventData);
                    SerializedMessage message =
                            new SerializedMessage(eventBytes, event.getMetadata(), event.getMessageId(),
                                                  event.getTimestamp().toEpochMilli());
                    fluxCapacitor.client().getEventStoreClient().storeEvents(aggregateId, "test", i,
                                                                             singletonList(message), false);
                } else {
                    fluxCapacitor.eventStore().storeEvents(aggregateId, aggregateId, i, event);
                }
            }
        });
    }

    @Override
    public When givenEvents(Object... events) {
        return given(() -> flatten(events).forEach(c -> fluxCapacitor.eventGateway().publish(c)));
    }

    @Override
    public When givenSchedules(Schedule... schedules) {
        return given(() -> Arrays.stream(schedules).forEach(s -> fluxCapacitor.scheduler().schedule(s)));
    }

    @Override
    public When given(Runnable condition) {
        return fluxCapacitor.execute(fc -> {
            try {
                condition.run();
                return this;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to execute given", e);
            }
        });
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
    public When andGivenDomainEvents(String aggregateId, Object... events) {
        return givenDomainEvents(aggregateId, events);
    }

    @Override
    public When andGivenSchedules(Schedule... schedules) {
        return givenSchedules(schedules);
    }

    @Override
    public When andGivenExpiredSchedules(Object... schedules) {
        return givenExpiredSchedules(schedules);
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
    public Then whenScheduleExpires(Object schedule) {
        return when(() -> fluxCapacitor.scheduler().schedule(interceptor.trace(schedule), getClock().instant()), false);
    }

    @Override
    public Then when(Runnable task) {
        return when(task, true);
    }

    @Override
    public Then whenApplying(Callable<?> task) {
        return applyWhen(task, true);
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

    protected Then applyWhen(Callable<?> action, boolean catchAll) {
        return fluxCapacitor.execute(fc -> {
            try {
                interceptor.startTrace(catchAll);
                Object result;
                try {
                    result = action.call();
                } catch (Exception e) {
                    result = e;
                }
                return createResultValidator(result);
            } finally {
                registration.cancel();
            }
        });
    }

    protected Then createResultValidator(Object result) {
        ResultValidator validator = new ResultValidator(getFluxCapacitor(), result, events, commands, schedules);
        synchronized (this) {
            if (!checkTrackers()) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return validator;
    }

    protected void advanceTimeBy(Duration duration) {
        advanceTimeTo(getClock().instant().plus(duration));
    }

    protected void advanceTimeTo(Instant instant) {
        withClock(Clock.fixed(instant, ZoneId.systemDefault()));
    }

    protected Then when(Runnable action, boolean catchAll) {
        return applyWhen(() -> {
            action.run();
            return null;
        }, catchAll);
    }

    protected void registerCommand(Message command) {
        commands.add(command);
    }

    protected void registerEvent(Message event) {
        events.add(event);
    }

    protected void registerSchedule(Schedule schedule) {
        schedules.add(schedule);
    }

    @SneakyThrows
    protected Object getDispatchResult(CompletableFuture<?> dispatchResult) {
        try {
            return dispatchResult.get(1L, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        } catch (TimeoutException e) {
            throw new TimeoutException("Test fixture did not receive a dispatch result in time. "
                                               + "Perhaps some messages did not have handlers?");
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

    protected boolean checkTrackers() {
        synchronized (this) {
            if (trackers.values().stream().allMatch(l -> l.stream().noneMatch(
                    m -> !(m instanceof Schedule) || !((Schedule) m).getDeadline().isAfter(getClock().instant())))) {
                notifyAll();
                return true;
            }
        }
        return false;
    }

    //todo juist omdraaien: alles wat niet in de when stap gebeurt moet getagged worden zodat je kan zien of t een effect is van de given of niet

    protected class GivenWhenThenInterceptor implements DispatchInterceptor, BatchInterceptor, HandlerInterceptor {

        private static final String TAG = "givenWhenThen.tag";
        private static final String TAG_NAME = "$givenWhenThen.tagName";
        private static final String TRACE_NAME = "$givenWhenThen.trace";

        private final Map<MessageType, List<Message>> publishedSchedules = new ConcurrentHashMap<>();

        private volatile boolean catchAll;

        public void startTrace(boolean catchAll) {
            this.catchAll = catchAll;
        }

        protected Message trace(Object message) {
            catchAll = false;
            Message result =
                    message instanceof Message ? (Message) message : new Message(message, Metadata.empty());
            return result.withMetadata(result.getMetadata().with(TAG_NAME, TAG));
        }

        protected boolean isDescendantMetadata(Metadata messageMetadata) {
            return TAG.equals(messageMetadata.getOrDefault(TRACE_NAME, "").split(",")[0]);
        }

        @Override
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                      MessageType messageType) {
            return message -> {
                Metadata metadata = message.getMetadata().addIfAbsent(TAG_NAME, UUID.randomUUID().toString());

                DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
                if (currentMessage != null) {
                    if (currentMessage.getMetadata().containsKey(TRACE_NAME)) {
                        metadata = metadata.with(TRACE_NAME, currentMessage.getMetadata().get(TRACE_NAME)
                                + "," + currentMessage.getMetadata().get(TAG_NAME));
                    } else {
                        metadata = metadata.with(TRACE_NAME, currentMessage.getMetadata().get(TAG_NAME));
                    }
                }
                message = message.withMetadata(metadata);

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
                SerializedMessage result = function.apply(message);

                Message interceptedMessage = message;
                if (messageType == MessageType.SCHEDULE) {
                    publishedSchedules.computeIfAbsent(MessageType.SCHEDULE, t -> new CopyOnWriteArrayList<>())
                            .add(interceptedMessage);
                }
                trackers.entrySet().stream()
                        .filter(t -> {
                            ConsumerConfiguration configuration = t.getKey().getConfiguration();
                            return (configuration.getMessageType() == messageType && Optional
                                    .ofNullable(configuration.getTypeFilter())
                                    .map(f -> interceptedMessage.getPayload().getClass().getName().matches(f))
                                    .orElse(true));
                        }).forEach(e -> {
                    e.getValue().add(interceptedMessage);
                });
                return result;
            };
        }

        @Override
        public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
            List<Message> messages =
                    publishedSchedules.getOrDefault(tracker.getConfiguration().getMessageType(), emptyList()).stream()
                            .filter(m -> Optional.ofNullable(tracker.getConfiguration().getTypeFilter())
                                    .map(f -> m.getPayload().getClass().getName().matches(f)).orElse(true))
                            .collect(toCollection(CopyOnWriteArrayList::new));
            trackers.put(tracker, messages);
            return b -> {
                consumer.accept(b);
                Collection<String> messageIds =
                        b.getMessages().stream().map(SerializedMessage::getMessageId).collect(toSet());
                messages.removeIf(m -> messageIds.contains(m.getMessageId()));
                checkTrackers();
            };
        }

        @Override
        public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                        Handler<DeserializingMessage> handler,
                                                                        String consumer) {
            return m -> {
                try {
                    return function.apply(m);
                } finally {
                    if (isLocalHandlerMethod(handler.getTarget().getClass(), handler.getMethod(m))) {
                        trackers.entrySet().stream()
                                .filter(t -> t.getKey().getConfiguration().getMessageType() == m.getMessageType())
                                .forEach(e -> e.getValue().removeIf(
                                        m2 -> m2.getMessageId().equals(m.getSerializedObject().getMessageId())));
                        checkTrackers();
                    }
                }
            };
        }
    }
}
