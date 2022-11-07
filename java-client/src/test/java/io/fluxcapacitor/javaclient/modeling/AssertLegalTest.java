package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Nullable;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
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
        testFixture.whenCommand(new CreateModelWithAssertion()).expectSuccessfulResult();
    }

    @Test
    void testUpdateWithLegalCheckOnNonExistingModelFails() {
        testFixture.whenCommand(new UpdateModelWithAssertion())
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testMultiAssert() {
        testFixture.whenApplying(
                        fc -> fc.aggregateRepository().load(aggregateId, TestModel.class)
                                .assertLegal(new CreateModel()).apply(new CreateModel())
                                .assertLegal(new CommandWithAssertionInInterface()))
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testAssertionViaInterface() {
        testFixture.whenCommand(new CommandWithAssertionInInterface())
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testMultipleAssertionMethods() {
        CommandWithMultipleAssertions
                command = new CommandWithMultipleAssertions();
        testFixture.whenCommand(command)
                .expectThat(fc -> assertEquals(3, command.getAssertionCount().get()));
    }

    @Test
    void testAssertionsForDifferentModels() {
        CommandWithAssertionsForDifferentModels
                command = new CommandWithAssertionsForDifferentModels();
        TestFixture.create()
                .whenExecuting(fc -> loadAggregate("1", Model1.class).assertLegal(command))
                .expectThat(fc -> assertEquals(1, command.getAssertionCount().get()));

        TestFixture.create()
                .whenExecuting(fc -> loadAggregate("2", Model2.class).assertLegal(command))
                .expectThat(fc -> assertEquals(3, command.getAssertionCount().get()));
    }

    @Test
    void testOverriddenAssertion() {
        testFixture.whenCommand(new CommandWithOverriddenAssertion()).expectSuccessfulResult();
    }

    @Test
    void testBatchAssertLegalInWhen() {
        testFixture.whenCommand(List.of("a", "b")).expectOnlyEvents(List.of("a", "b"));
    }

    @Test
    void testAssertLegalAfterApply() {
        testFixture.whenCommand(new CommandWithAssertAfterApply()).expectExceptionalResult(MockException.class);
    }

    @Test
    void testAssertInField() {
        testFixture.whenCommand(new CommandThatDelegatesToProperty(new CommandWithAssertionInInterface(), null))
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testAssertInMethod() {
        testFixture.whenCommand(new CommandThatDelegatesToProperty(null, new CommandWithAssertionInInterface()))
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testAssertInFieldOrMethodIfBothAreNull() {
        testFixture.whenCommand(new CommandThatDelegatesToProperty(null, null)).expectSuccessfulResult();
    }

    @Test
    void testFollowUpAssertionFromReturnedValue() {
        testFixture.whenCommand(new CommandWithAssertThatReturnsValue()).expectExceptionalResult(MockException.class);
    }

    @Test
    void assertInRootEntity() {
        testFixture.givenCommands(new CreateModel()).whenCommand(12)
                .expectExceptionalResult(IllegalCommandException.class);
    }

    @Test
    void assertInRootEntity_allowed() {
        testFixture.givenCommands(new CreateModel()).whenCommand(5).expectSuccessfulResult();
    }

    @Test
    void assertInRootEntityBeforeCreate() {
        testFixture.whenCommand(5).expectExceptionalResult(IllegalCommandException.class);
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command) {
            loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command);
        }

        @HandleCommand
        void handle(List<?> commands) {
            Entity<TestModel> root = loadAggregate(aggregateId, TestModel.class);
            commands.forEach(c -> root.assertLegal(c).apply(c));
        }
    }

    @Aggregate
    private static class TestModel {
        @Apply
        public TestModel(Object command) {
        }

        @AssertLegal
        static void staticAssertNotLargerThan10(Integer number) {
            if (number > 2) {
                throw new IllegalCommandException("Number is larger than 2");
            }
        }

        @AssertLegal
        void assertNotLargerThan10(Integer number) {
            if (number > 10) {
                throw new IllegalCommandException("Number is larger than 10");
            }
        }
    }

    @Value
    private static class CreateModel {
    }

    @Value
    private static class CreateModelWithAssertion {
        @AssertLegal
        private void assertDoesNotExist(TestModel model) {
            if (model != null) {
                throw new MockException("Model should not exist");
            }
        }
    }

    @Value
    private static class UpdateModelWithAssertion {
        @AssertLegal
        private void assertExists(@Nullable TestModel model) {
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
        default void assertTheImpossible(@Nullable Object model) {
            throw new MockException();
        }
    }

    @Value
    private static class CommandWithMultipleAssertions {
        AtomicInteger assertionCount = new AtomicInteger();

        @AssertLegal
        private void assert1(@Nullable Object model) {
            assertionCount.addAndGet(1);
        }

        @AssertLegal(priority = HIGHEST_PRIORITY)
        private void assert2(@Nullable Object model) {
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
        private void assert1(@Nullable Model1 model) {
            assertionCount.addAndGet(1);
        }

        @AssertLegal
        private void assert2(@Nullable Model2 model) {
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

        @Apply
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

    @Value
    private static class CommandWithAssertAfterApply {
        @AssertLegal(afterHandler = true)
        void assertAfterApply(TestModel model) {
            if (model != null) {
                throw new MockException();
            }
        }

        @Apply
        TestModel apply() {
            return new TestModel(this);
        }
    }

    @Value
    private static class CommandThatDelegatesToProperty {
        @AssertLegal
        CommandWithAssertionInInterface field;
        CommandWithAssertionInInterface method;

        @AssertLegal
        public CommandWithAssertionInInterface getMethod() {
            return method;
        }
    }

    @Value
    private static class CommandWithAssertThatReturnsValue {
        @AssertLegal
        CommandWithAssertionInInterface followUpAssertion(@Nullable TestModel model) {
            return new CommandWithAssertionInInterface();
        }
    }

}
