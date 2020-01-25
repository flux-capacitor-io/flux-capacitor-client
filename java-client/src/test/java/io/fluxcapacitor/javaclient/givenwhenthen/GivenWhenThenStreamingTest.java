package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.IgnoringErrorHandler;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static java.util.stream.Collectors.toList;

class GivenWhenThenStreamingTest {

    private final CommandHandler commandHandler = new CommandHandler();
    private final AsyncCommandHandler asyncCommandHandler = new AsyncCommandHandler();
    private final EventHandler eventHandler = new EventHandler();
    private final StreamingTestFixture
            subject = StreamingTestFixture.create(DefaultFluxCapacitor.builder().configureDefaultConsumer(
                    COMMAND, config -> config.toBuilder().errorHandler(new IgnoringErrorHandler()).build()), 
                                                  commandHandler, eventHandler, asyncCommandHandler);

    @Test
    void testExpectCommandsAndIndirectEvents() {
        subject.whenEvent(123).expectNoResult().expectCommands(new YieldsEventAndResult()).expectEvents(new YieldsEventAndResult());
    }

    @Test
    void testExpectFunctionalException() {
        subject.whenCommand(new YieldsException()).expectException(MockException.class);
    }

    @Test
    void testExpectTechnicalException() {
        subject.whenCommand(new YieldsRuntimeException()).expectException(TechnicalException.class);
    }

    @Test
    void testAsyncCommandHandling() {
        subject.whenCommand(new YieldsAsyncResult()).expectResult("test");
    }

    @Test
    void testExpectPassiveHandling() {
        subject.givenNoPriorActivity().whenCommand(new PassivelyHandled()).expectException(TimeoutException.class);
    }

    @Test
    void testCommandBatch() {
        List<HandledInBatchYieldsList> commands =
                IntStream.range(0, 4).mapToObj(i -> new HandledInBatchYieldsList()).collect(toList());
        subject.givenCommands(commands.subList(0, 3)).whenCommand(commands.get(3)).expectResult(commands.get(3));
    }

    @Test
    void testCommandBatchYieldsId() {
        List<HandledInBatchYieldsId> commands =
                IntStream.range(0, 4).mapToObj(i -> new HandledInBatchYieldsId()).collect(toList());
        subject.givenCommands(Stream.concat(commands.subList(0, 3).stream(), Stream.of("bla")).collect(toList()))
                .whenCommand(commands.get(3)).expectResult(commands.get(3).getId());
    }

    @Test
    void testCommandBatchYieldsIdAsync() {
        List<HandledInBatchYieldsIdAsync> commands =
                IntStream.range(0, 4).mapToObj(i -> new HandledInBatchYieldsIdAsync()).collect(toList());
        subject.givenCommands(commands.subList(0, 3)).whenCommand(commands.get(3)).expectResult(commands.get(3).getId());
    }

    @Test
    void testAnyOtherList() {
        List<String> commands = IntStream.range(0, 4).mapToObj(Integer::toString).collect(toList());
        subject.givenCommands(commands.subList(0, 3)).whenCommand(commands.get(3)).expectResult(commands.get(3));
    }

    private static class CommandHandler {
        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }

        @HandleCommand
        public void handle(YieldsException command) {
            throw new MockException("expected");
        }

        @HandleCommand
        public void handle(YieldsRuntimeException command) {
            throw new IllegalStateException("expected");
        }

        @HandleCommand(passive = true)
        public String handle(PassivelyHandled command) {
            return "this will be ignored";
        }

        @HandleCommand
        public List<HandledInBatchYieldsList> handleBatch(List<HandledInBatchYieldsList> commandBatch) {
            return commandBatch;
        }

        @HandleCommand
        public String handleBatchYieldsId(List<HandledInBatchYieldsId> commandBatch) {
            return commandBatch.get(commandBatch.size() - 1).getId();
        }

        @HandleCommand
        public List<CompletableFuture<?>> handleBatchYieldsFuture(List<HandledInBatchYieldsIdAsync> commandBatch) {
            return commandBatch.stream().map(c -> {
                CompletableFuture<Object> result = new CompletableFuture<>();
                result.complete(c.getId());
                return result;
            }).collect(toList());
        }
        
        @HandleCommand
        public List<?> handleAnyStringList(List<? extends String> commands) {
            return commands;
        }
    }

    private static class EventHandler {
        @HandleEvent
        public void handle(Integer event) throws Exception {
            FluxCapacitor.sendCommand(new YieldsEventAndResult()).get();
        }
    }
    
    private static class AsyncCommandHandler {
        
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        @HandleCommand
        public CompletableFuture<String> handle(YieldsAsyncResult command) {
            CompletableFuture<String> result = new CompletableFuture<>();
            scheduler.schedule(() -> result.complete("test"), 10, TimeUnit.MILLISECONDS);
            return result;
        }
    }
    
    @Value
    private static class YieldsEventAndResult {
    }

    @Value
    private static class YieldsAsyncResult {
    }

    @Value
    private static class YieldsException {
    }

    @Value
    private static class YieldsRuntimeException {
    }

    @Value
    private static class PassivelyHandled {
    }

    @Value
    private static class HandledInBatchYieldsList {
        String id = UUID.randomUUID().toString();
    }

    @Value
    private static class HandledInBatchYieldsId {
        String id = UUID.randomUUID().toString();
    }

    @Value
    private static class HandledInBatchYieldsIdAsync {
        String id = UUID.randomUUID().toString();
    }
}
