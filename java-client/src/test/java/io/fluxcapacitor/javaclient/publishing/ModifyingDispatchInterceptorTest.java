package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;

public class ModifyingDispatchInterceptorTest {

    @Test
    void testThatInterceptorChangesMessageTypeSuccessfully() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeTypeInterceptor(), COMMAND),
                        new CommandHandler())
                
                .whenCommand(new Command(""))
                .expectEvents(new DifferentCommand());
    }

    @Test
    void testThatInterceptorChangesMessageContentSuccessfully() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeContentInterceptor(), COMMAND),
                        new CommandHandler())
                
                .whenCommand(new Command(""))
                .expectEvents(new Command("intercepted"));
    }


    public static class ChangeTypeInterceptor implements DispatchInterceptor {
        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            return message.withPayload(new DifferentCommand());
        }
    }

    public static class ChangeContentInterceptor implements DispatchInterceptor {
        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            return message.withPayload(new Command("intercepted"));
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
