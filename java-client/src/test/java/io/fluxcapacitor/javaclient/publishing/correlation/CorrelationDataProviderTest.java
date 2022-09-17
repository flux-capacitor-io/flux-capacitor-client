package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

class CorrelationDataProviderTest {
    private final CorrelationDataProvider testProvider = msg -> {
        Map<String, String> result = new HashMap<>(Map.of("foo", "bar"));
        if (msg != null) {
            result.put("msgId", msg.getMessageId());
        }
        return result;
    };
    private final DefaultCorrelationDataProvider defaultProvider = DefaultCorrelationDataProvider.INSTANCE;

    @Test
    void provideCommandAndEventMetadata() {
        var command = new Message("bla");
        TestFixture.create(DefaultFluxCapacitor.builder().replaceCorrelationDataProvider(
                defaultProvider -> testProvider), new CommandHandler())
                .whenExecuting(fc -> fc.commandGateway().sendAndForget(command))
                .expectCommands(command.addMetadata("foo", "bar"))
                .expectEvents(command.addMetadata("foo", "bar", "msgId", command.getMessageId()));
    }

    @Test
    void extendDefaultProvider() {
        var command = new Message("bla");
        TestFixture.create(DefaultFluxCapacitor.builder().replaceCorrelationDataProvider(
                defaultProvider -> defaultProvider.andThen(testProvider)), new CommandHandler())
                .whenExecuting(fc -> fc.commandGateway().sendAndForget(command))
                .expectCommands(command.addMetadata("foo", "bar"))
                .expectCommands((Predicate<Message>) c -> c.getMetadata().containsKey(defaultProvider.getClientIdKey()))
                .expectEvents(command.addMetadata("foo", "bar", "msgId", command.getMessageId(),
                                                  defaultProvider.getCorrelationIdKey(), command.getMessageId()));
    }

    private static class CommandHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.publishEvent(command);
        }
    }

}