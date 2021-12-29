package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

public class GivenWhenThenInterceptorTest {

    @Test
    void testThatInterceptorChangesMessageTypeSuccessfully() {
        TestFixture.createAsync(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeTypeInterceptor(), MessageType.COMMAND),
                        new CommandHandler())
                .givenNoPriorActivity()
                .whenCommand(new Command(""))
                .expectEvents(new DifferentCommand());
    }

    @Test
    void testThatInterceptorChangesMessageContentSuccessfully() {
        TestFixture.createAsync(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeContentInterceptor(), MessageType.COMMAND),
                        new CommandHandler())
                .givenNoPriorActivity()
                .whenCommand(new Command(""))
                .expectEvents(new Command("intercepted"));
    }


    public static class ChangeTypeInterceptor implements DispatchInterceptor {
        @Override
        public final Function<Message, SerializedMessage> interceptDispatch(
                Function<Message, SerializedMessage> function, MessageType messageType) {
            return m -> function.apply(new Message(new DifferentCommand(), m.getMetadata()));
        }
    }

    public static class ChangeContentInterceptor implements DispatchInterceptor {
        @Override
        public final Function<Message, SerializedMessage> interceptDispatch(
                Function<Message, SerializedMessage> function, MessageType messageType) {
            return m -> function.apply(new Message(new Command("intercepted"), m.getMetadata()));
        }
    }

    private static class CommandHandler {
        @HandleCommand
        public void handle(Object command) {
            FluxCapacitor.publishEvent(command);
        }
    }

    @Value
    public static class Command {
        String content;
    }

    @Value
    public static class DifferentCommand {
    }
}
