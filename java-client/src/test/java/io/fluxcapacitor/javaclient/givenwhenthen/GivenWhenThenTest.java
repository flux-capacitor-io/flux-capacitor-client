/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.metrics.ProcessBatchEvent;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class GivenWhenThenTest {

    private final CommandHandler commandHandler = spy(new CommandHandler());
    private TestFixture testFixture = TestFixture.create(commandHandler);

    @Test
    void testInjectingMockBeans() {
        testFixture.withBean(new MockBean()).whenCommand(new YieldsMockBean()).expectResult(MockBean.class);
    }

    @Nested
    class AndThen {
        @Test
        void testAndThen_sync() {
            YieldsEventAndResult second = new YieldsEventAndResult();
            testFixture.whenCommand(new YieldsNoResult()).expectNoEvents()
                    .andThen().whenCommand(second).expectOnlyEvents(second).expectResult(String.class);
        }

        @Test
        void testAndThenGiven() {
            YieldsEventAndResult second = new YieldsEventAndResult();
            testFixture.whenCommand(new YieldsNoResult()).expectNoEvents()
                    .andThen()
                    .givenCommands(new YieldsEventAndNoResult())
                    .whenCommand(second).expectOnlyEvents(second).expectResult(String.class);
        }

        @Test
        void testAndThen_async() {
            YieldsEventAndResult second = new YieldsEventAndResult();
            testFixture.async().whenCommand(new YieldsNoResult()).expectNoEvents()
                    .andThen().whenCommand(second).expectOnlyEvents(second).expectResult(String.class);
        }
    }

    @Test
    void registeringHandlerAsClassWorks() {
        TestFixture.create(CommandHandler.class).whenCommand(new YieldsEventAndResult())
                .expectOnlyEvents(new YieldsEventAndResult())
                .expectResult(String.class);
    }

    @Test
    void registeringHandlerAsClassWorks_async() {
        TestFixture.createAsync(CommandHandler.class).whenCommand(new YieldsEventAndResult())
                .expectOnlyEvents(new YieldsEventAndResult())
                .expectResult(String.class);
    }

    @Test
    void testExpectNoEventsAndNoResult() {
        testFixture.whenCommand(new YieldsNoResult()).expectNoEvents().expectNoResult();
    }

    @Test
    void testExpectResultButNoEvents() {
        testFixture.whenCommand(new YieldsResult()).expectNoEvents().expectResult(String.class);
    }

    @Test
    void testExpectExceptionButNoEvents() {
        testFixture.whenCommand(new YieldsException()).expectNoEvents()
                .expectExceptionalResult(MockException.class);
    }

    @Test
    void testInvokeMostGenericHandler() {
        testFixture.whenCommand("some string").expectEvents("generic");
    }

    @Test
    void testExpectEventButNoResult() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        testFixture.whenCommand(command)
                .expectOnlyEvents(command).expectNoResult().expectSuccessfulResult();
    }

    @Test
    void testExpectNoEventsLike() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        testFixture.whenCommand(command).expectNoEventsLike(String.class);
    }

    @Test
    void testExpectResultAndEvent() {
        YieldsEventAndResult command = new YieldsEventAndResult();
        testFixture.whenCommand(command).expectOnlyEvents(command).expectResult(String.class);
    }

    @Test
    void testExpectResultUsingPredicate() {
        YieldsResult command = new YieldsResult();
        testFixture.whenCommand(command).expectResult("result"::equals);
    }

    @Test
    void testExpectExceptionAndEvent() {
        YieldsEventAndException command = new YieldsEventAndException();
        testFixture.whenCommand(command).expectOnlyEvents(command)
                .expectExceptionalResult(MockException.class)
                .expectError(MockException.class);
    }

    @Test
    void testWithGivenCommandsAndResult() {
        testFixture.givenCommands(new YieldsNoResult()).whenCommand(new YieldsResult()).expectResult(String.class)
                .expectNoEvents()
                .expectNoErrors();
    }

    @Test
    void testWithGivenCommandsAndNoResult() {
        testFixture.givenCommands(new YieldsResult()).whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromGiven() {
        testFixture.givenCommands(new YieldsEventAndResult()).whenCommand(new YieldsNoResult()).expectNoResult()
                .expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromCommand() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        testFixture.givenCommands(new YieldsNoResult()).whenCommand(command).expectNoResult().expectEvents(command);
    }

    @Test
    void testWithMultipleGivenCommands() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        testFixture.givenCommands(new YieldsNoResult(), new YieldsResult(), command, command).whenCommand(command)
                .expectNoResult().expectOnlyEvents(command);
    }

    @Test
    void testAndGivenCommands() {
        testFixture.givenCommands(new YieldsResult()).givenCommands(new YieldsEventAndNoResult())
                .whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
        InOrder inOrder = inOrder(commandHandler);
        inOrder.verify(commandHandler).handle(new YieldsResult());
        inOrder.verify(commandHandler).handle(new YieldsEventAndNoResult());
        inOrder.verify(commandHandler).handle(new YieldsNoResult());
    }

    @Test
    void testMultiHandler() {
        testFixture = TestFixture.create(commandHandler, new EventHandler());
        testFixture.whenCommand(new YieldsEventAndNoResult())
                .expectEvents(new YieldsEventAndNoResult())
                .expectCommands(new YieldsNoResult());
    }

    @Test
    void testMultiHandlerWithExceptionInEventHandler() {
        testFixture = TestFixture.create(commandHandler, new ThrowingEventHandler());
        testFixture.whenCommand(new YieldsEventAndNoResult())
                .expectEvents(new YieldsEventAndNoResult())
                .expectSuccessfulResult()
                .expectError(MockException.class);
    }

    @Test
    void testGivenCondition() {
        Runnable mockCondition = mock(Runnable.class);
        testFixture.given(fc -> mockCondition.run()).whenCommand(new YieldsNoResult())
                .expectThat(fc -> verify(mockCondition).run());
    }

    @Test
    void testWhenCondition() {
        Runnable mockCondition = mock(Runnable.class);
        testFixture.whenExecuting(fc -> mockCondition.run()).expectThat(fc -> verify(mockCondition).run());
    }

    @Test
    void testGivenAppliedEvents() {
        testFixture.givenAppliedEvents("test", MockAggregate.class, new MockAggregateEvent())
                .whenApplying(fc -> loadAggregate("test", MockAggregate.class).get())
                .expectResult(r -> r instanceof MockAggregate);
    }

    @Test
    void testGivenCommandsAsJson() {
        testFixture.givenCommands("yields-result.json").whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
    }

    @Test
    void testExpectAsJson() {
        testFixture.whenCommand("yields-result.json").expectResult("result.json");
    }

    @Test
    void testExpectMetrics() {
        TestFixture.createAsync(commandHandler).whenCommand(new YieldsNoResult())
                .expectMetrics(ProcessBatchEvent.class);
    }

    @Test
    void testExpectNoMetricsLike() {
        TestFixture.createAsync(commandHandler).whenCommand(new YieldsNoResult()).expectNoMetricsLike(String.class);
    }

    private static class CommandHandler {
        @HandleCommand
        public void handle(YieldsNoResult command) {
            //no op
        }

        @HandleCommand
        public String handle(YieldsResult command) {
            return "result";
        }

        @HandleCommand(allowedClasses = YieldsException.class)
        public void handleYieldException() {
            throw new MockException();
        }

        @HandleCommand(allowedClasses = Object.class)
        public void handleGeneric() {
            FluxCapacitor.publishEvent("generic");
        }

        @HandleCommand
        public void handle(YieldsEventAndNoResult command) {
            FluxCapacitor.publishEvent(command);
        }

        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }

        @HandleCommand
        public void handle(YieldsEventAndException command) {
            FluxCapacitor.publishEvent(command);
            throw new MockException();
        }

        @HandleCommand
        public MockBean handle(YieldsMockBean command, @Autowired MockBean mockBean) {
            return mockBean;
        }
    }

    private static class EventHandler {
        @HandleEvent
        public void handle(Object event) {
            FluxCapacitor.sendCommand(new YieldsNoResult());
        }
    }

    private static class ThrowingEventHandler {
        @HandleEvent
        public void handle(Object event) {
            throw new MockException();
        }
    }

    @Aggregate
    private static class MockAggregate {
        @Apply
        MockAggregate(MockAggregateEvent event) {
        }
    }

    @Value
    private static class MockAggregateEvent {
    }

    @Value
    private static class YieldsNoResult {
    }

    @Value
    private static class YieldsResult {
    }

    @Value
    private static class YieldsException {
    }

    @Value
    private static class YieldsEventAndNoResult {
    }

    @Value
    private static class YieldsEventAndResult {
    }

    @Value
    private static class YieldsEventAndException {
    }

    @Value
    private static class YieldsMockBean {
    }

    @Value
    static class MockBean {
    }
}
