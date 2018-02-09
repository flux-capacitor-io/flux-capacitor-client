package io.fluxcapacitor.javaclient.test;

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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.COMMAND;

public class TestFixture implements Given, When {

    private final FluxCapacitor fluxCapacitor;
    private final Registration registration;
    private final GivenWhenThenInterceptor interceptor;
    private final BlockingQueue<Message> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> commands = new LinkedBlockingQueue<>();

    public static TestFixture create(Object... handlers) {
        return new TestFixture(DefaultFluxCapacitor.builder(), handlers);
    }

    public static TestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new TestFixture(fluxCapacitorBuilder, handlers);
    }

    protected TestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        this.interceptor = new GivenWhenThenInterceptor();
        this.fluxCapacitor = fluxCapacitorBuilder.addDispatchInterceptor(interceptor).addHandlerInterceptor(interceptor)
                .build(InMemoryClient.newInstance());
        this.registration = fluxCapacitor.startTracking(handlers);
    }

    @Override
    public When givenCommands(Object... commands) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Arrays.stream(commands).forEach(c -> fluxCapacitor.commandGateway().sendAndForget(c));
            return this;
        } finally {
            FluxCapacitor.instance.remove();
        }
    }

    @Override
    public When andGivenCommands(Object... commands) {
        return givenCommands(commands);
    }

    @Override
    public Then whenCommand(Object command) {
        try {
            FluxCapacitor.instance.set(fluxCapacitor);
            Message commandMessage = interceptor.traceCommand(command);
            Object result;
            try {
                result = fluxCapacitor.commandGateway().send(commandMessage).get(1L, TimeUnit.SECONDS);
            } catch (Exception e) {
                result = e;
            }
            return new ResultValidator(commandMessage, result, events, commands);
        } finally {
            registration.cancel();
            FluxCapacitor.instance.remove();
        }
    }

    protected class GivenWhenThenInterceptor extends ContextualDispatchInterceptor {

        private static final String TAG = "givenWhenThen.tag";
        private static final String TAG_NAME = "givenWhenThen.tagName";
        private static final String TRACE_NAME = "givenWhenThen.trace";

        protected Message traceCommand(Object command) {
            Message result = command instanceof Message ? (Message) command : new Message(command, Metadata.empty(), COMMAND);
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
