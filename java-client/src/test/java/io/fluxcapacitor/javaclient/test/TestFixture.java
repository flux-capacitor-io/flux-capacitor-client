package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.publishing.correlation.ContextualDispatchInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestFixture implements Given, When {

    private final ScheduledExecutorService deregistrationService = Executors.newSingleThreadScheduledExecutor();
    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;
    private final BlockingQueue<Message> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> commands = new LinkedBlockingQueue<>();

    public static TestFixture create(Object... handlers) {
        return new TestFixture(DefaultFluxCapacitor.builder(), fc -> Arrays.asList(handlers));
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new TestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static TestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(DefaultFluxCapacitor.builder(), handlersFactory);
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        return new TestFixture(fluxCapacitorBuilder, handlersFactory);
    }

    protected TestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlers) {
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder.addDispatchInterceptor(interceptor).addHandlerInterceptor(interceptor)
                .build(InMemoryClient.newInstance());
        this.registration = fluxCapacitor.startTracking(handlers.apply(fluxCapacitor));
    }

    @Override
    public When givenCommands(Object... commands) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            CompletableFuture.allOf(Arrays.stream(commands).map(c -> fluxCapacitor.commandGateway().send(c))
                                            .toArray(CompletableFuture[]::new)).get(2L, SECONDS);
            return this;
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Failed to execute givenCommands due to a timeout. "
                            + "Make sure all given commands are handled by the application under test.", e);
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
            Arrays.stream(events).forEach(c -> fluxCapacitor.eventGateway().publish(c));
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute givenEvents", e);
        } finally {
            FluxCapacitor.instance.remove();
        }
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
    public Then whenCommand(Object command) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = fluxCapacitor.commandGateway().send(interceptor.trace(command, COMMAND)).get(1L, SECONDS);
            } catch (ExecutionException e) {
                result = e.getCause();
            } catch (Exception e) {
                result = e;
            }
            return new ResultValidator(result, events, commands);
        } finally {
            deregistrationService.schedule(registration::cancel, 1L, SECONDS);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenEvent(Object event) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            fluxCapacitor.eventGateway().publish(interceptor.trace(event, EVENT));
            return new ResultValidator(null, events, commands);
        } finally {
            deregistrationService.schedule(registration::cancel, 1L, SECONDS);
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public Then whenQuery(Object query) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Object result;
            try {
                result = fluxCapacitor.queryGateway().query(query).get(1L, SECONDS);
            } catch (ExecutionException e) {
                result = e.getCause();
            } catch (Exception e) {
                result = e;
            }
            return new ResultValidator(result, events, commands);
        } finally {
            deregistrationService.schedule(registration::cancel, 1L, SECONDS);
            FluxCapacitor.instance.remove();
        }
    }

    protected class GivenWhenThenInterceptor extends ContextualDispatchInterceptor {

        private static final String TAG = "givenWhenThen.tag";
        private static final String TAG_NAME = "givenWhenThen.tagName";
        private static final String TRACE_NAME = "givenWhenThen.trace";

        protected Message trace(Object message, MessageType type) {
            Message result =
                    message instanceof Message ? (Message) message : new Message(message, Metadata.empty(), type);
            result.getMetadata().put(TAG_NAME, TAG);
            return result;
        }

        protected boolean isChildMetadata(Metadata messageMetadata) {
            return TAG.equals(messageMetadata.get(TRACE_NAME));
        }

        protected boolean isDescendantMetadata(Metadata messageMetadata) {
            return TAG.equals(getTrace(messageMetadata).get(0));
        }

        protected List<String> getTrace(Metadata messageMetadata) {
            return Arrays.asList(messageMetadata.getOrDefault(TRACE_NAME, "").split(","));
        }

        @Override
        public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function) {
            return message -> {
                String tag = UUID.randomUUID().toString();
                message.getMetadata().putIfAbsent(TAG_NAME, tag);
                getCurrentMessage().ifPresent(currentMessage -> {
                    if (currentMessage.getMetadata().containsKey(TRACE_NAME)) {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(
                                TRACE_NAME) + "," + currentMessage.getMetadata().get(TAG_NAME));
                    } else {
                        message.getMetadata().put(TRACE_NAME, currentMessage.getMetadata().get(TAG_NAME));
                    }
                });
                if (isDescendantMetadata(message.getMetadata())) {
                    switch (message.getMessageType()) {
                        case COMMAND:
                            commands.add(message);
                            break;
                        case EVENT:
                            events.add(message);
                            break;
                    }
                }
                return function.apply(message);
            };
        }
    }
}
