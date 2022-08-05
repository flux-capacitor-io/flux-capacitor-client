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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

class GivenWhenThenMultiHandlerTest {

    private final CommandHandler commandHandler = spy(new CommandHandler());
    private final EventHandler eventHandler = spy(new EventHandler());
    private final QueryHandler queryHandler = spy(new QueryHandler());
    private final TestFixture
            subject = TestFixture.create(commandHandler, eventHandler, queryHandler);

    @Test
    void testExpectNoEventsAndNoResult() {
        subject.givenNoPriorActivity().whenCommand(new YieldsNoResult()).expectNoEvents().expectNoResult();
    }

    @Test
    void testExpectResultButNoEvents() {
        subject.givenNoPriorActivity().whenCommand(new YieldsResult()).expectNoEvents().expectResult(String.class);
    }

    @Test
    void testExpectExceptionButNoEvents() {
        subject.givenNoPriorActivity().whenCommand(new YieldsFunctionalException()).expectNoEvents().expectExceptionalResult(FunctionalMockException.class);
    }

    @Test
    void testExpectEventButNoResult() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command).expectNoResult();
    }

    @Test
    void testExpectResultAndEvent() {
        YieldsEventAndResult command = new YieldsEventAndResult();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command).expectResult(String.class);
    }

    @Test
    void testExpectExceptionAndEvent() {
        YieldsEventAndException command = new YieldsEventAndException();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command).expectExceptionalResult(Exception.class);
    }

    @Test
    void testWithGivenCommandsAndResult() {
        subject.givenCommands(new YieldsNoResult()).whenCommand(new YieldsResult()).expectResult(String.class).expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndNoResult() {
        subject.givenCommands(new YieldsResult()).whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromGiven() {
        subject.givenCommands(new YieldsEventAndResult()).whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromCommand() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenCommands(new YieldsNoResult()).whenCommand(command).expectNoResult().expectEvents(command);
    }

    @Test
    void testWithMultipleGivenCommands() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenCommands(new YieldsNoResult(), new YieldsResult(), command, command).whenCommand(command).expectNoResult().expectOnlyEvents(command);
    }

    @Test
    void testAndGivenCommands() {
        subject.givenCommands(new YieldsResult()).givenCommands(new YieldsEventAndNoResult()).whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
        InOrder inOrder = inOrder(commandHandler);
        inOrder.verify(commandHandler).handle(new YieldsResult());
        inOrder.verify(commandHandler).handle(new YieldsEventAndNoResult());
        inOrder.verify(commandHandler).handle(new YieldsNoResult());
    }

    @Test
    void testExpectCommands() {
        subject.whenEvent("some event").expectCommands(new YieldsNoResult()).expectNoEvents().expectNoResult();
    }

    @Test
    void testExpectCommandsAndIndirectEvents() {
        subject.whenEvent(123).expectNoResult().expectCommands(new YieldsEventAndResult()).expectEvents(new YieldsEventAndResult());
    }

    @Test
    void testQuery() {
        subject.whenQuery("bla").expectResult("bla");
    }

    @Test
    void testFailingQuery() {
        subject.whenQuery(1L).expectExceptionalResult(Exception.class);
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

        @HandleCommand
        public void handle(YieldsTechnicalException command) {
            throw new MockException();
        }

        @HandleCommand
        public void handle(YieldsFunctionalException command) {
            throw new FunctionalMockException();
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
    }

    private static class EventHandler {
        @HandleEvent
        public void handle(String event) {
            FluxCapacitor.sendCommand(new YieldsNoResult());
        }

        @HandleEvent
        public void handle(Integer event) throws Exception {
            FluxCapacitor.sendCommand(new YieldsEventAndResult()).get();
        }
    }

    private static class QueryHandler {
        @HandleQuery
        public String handle(String query) {
            return query;
        }

        @HandleQuery
        public String handleButFail(Long query) {
            throw new MockException();
        }
    }

    @Value
    private static class YieldsNoResult {
    }

    @Value
    private static class YieldsResult {
    }

    @Value
    private static class YieldsTechnicalException {
    }

    @Value
    private static class YieldsFunctionalException {
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

    private static class FunctionalMockException extends FunctionalException {
    }

}
