package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.modeling.AssertLegal.HIGHEST_PRIORITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AssertLegalTest {

    private static final String aggregateId = "test";
    private final TestFixture testFixture = TestFixture.create(new Handler());

    @Test
    void testCreateWithLegalCheckOnNonExistingModelSucceeds() {
        testFixture.givenNoPriorActivity().whenCommand(new CreateModelWithAssertion()).expectNoException();
    }

    @Test
    void testUpdateWithLegalCheckOnNonExistingModelFails() {
        testFixture.givenNoPriorActivity().whenCommand(new UpdateModelWithAssertion())
                .expectException(MockException.class);
    }

    @Test
    void testMultiAssert() {
        testFixture.givenNoPriorActivity().whenApplying(
                        fc -> fc.aggregateRepository().load(aggregateId, TestModel.class)
                                .assertLegal(new CreateModel(), new CommandWithAssertionInInterface()))
                .expectException(MockException.class);
    }

    @Test
    void testAssertionViaInterface() {
        testFixture.givenNoPriorActivity().whenCommand(new CommandWithAssertionInInterface())
                .expectException(MockException.class);
    }

    @Test
    void testMultipleAssertionMethods() {
        CommandWithMultipleAssertions
                command = new CommandWithMultipleAssertions();
        testFixture.givenNoPriorActivity().whenCommand(command)
                .expectThat(fc -> assertEquals(3, command.getAssertionCount().get()));
    }

    @Test
    void testAssertionsForDifferentModels() {
        CommandWithAssertionsForDifferentModels
                command = new CommandWithAssertionsForDifferentModels();
        TestFixture.create()
                .when(fc -> loadAggregate("1", Model1.class, false).assertLegal(command))
                .expectThat(fc -> assertEquals(1, command.getAssertionCount().get()));

        TestFixture.create()
                .when(fc -> loadAggregate("2", Model2.class, false).assertLegal(command))
                .expectThat(fc -> assertEquals(3, command.getAssertionCount().get()));
    }

    @Test
    void testOverriddenAssertion() {
        testFixture.givenNoPriorActivity().whenCommand(new CommandWithOverriddenAssertion()).expectNoException();
    }

    @Test
    void testBatchAssertLegalInWhen() {
        testFixture.whenCommand(List.of("a", "b")).expectOnlyEvents(List.of("a", "b"));
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command) {
            loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command);
        }

        @HandleCommand
        void handle(List<?> commands) {
            loadAggregate(aggregateId, TestModel.class).assertLegal("some", "other", "commands").apply(commands);
        }
    }

    @Aggregate
    private static class TestModel {
        @ApplyEvent
        public TestModel(Object command) {
        }
    }

    @Value
    private static class CreateModel {
    }

    @Value
    private static class CreateModelWithAssertion {
        @AssertLegal
        private void assertDoesNotExist(Object model) {
            if (model != null) {
                throw new MockException("Model should not exist");
            }
        }
    }

    @Value
    private static class UpdateModelWithAssertion {
        @AssertLegal
        private void assertExists(Object model) {
            if (model == null) {
                throw new MockException("Model should exist");
            }
        }
    }

    @Value
    private static class CommandWithAssertionInInterface implements ImpossibleAssertion {
    }

    private interface ImpossibleAssertion {
        @AssertLegal
        default void assertTheImpossible(Object model) {
            throw new MockException();
        }
    }

    @Value
    private static class CommandWithMultipleAssertions {
        AtomicInteger assertionCount = new AtomicInteger();

        @AssertLegal
        private void assert1(Object model) {
            assertionCount.addAndGet(1);
        }

        @AssertLegal(priority = HIGHEST_PRIORITY)
        private void assert2(Object model) {
            if (assertionCount.get() > 0) {
                throw new IllegalStateException("Expected to come first");
            }
            assertionCount.addAndGet(2);
        }
    }

    @Value
    private static class CommandWithAssertionsForDifferentModels {
        AtomicInteger assertionCount = new AtomicInteger();

        @AssertLegal
        private void assert1(Model1 model) {
            assertionCount.addAndGet(1);
        }

        @AssertLegal
        private void assert2(Model2 model) {
            assertionCount.addAndGet(2);
        }
    }

    @Aggregate
    @Value
    private static class Model1 {
    }

    @Aggregate
    @Value
    private static class Model2 {
        String event;

        @ApplyEvent
        static Model2 apply(String event) {
            return new Model2(event);
        }
    }

    @Value
    private static class CommandWithOverriddenAssertion implements ImpossibleAssertion {
        @Override
        @AssertLegal
        public void assertTheImpossible(Object model) {
            //do nothing
        }
    }
}
