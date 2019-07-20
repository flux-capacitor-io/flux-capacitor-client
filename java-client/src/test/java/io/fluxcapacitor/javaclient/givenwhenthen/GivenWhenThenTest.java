/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class GivenWhenThenTest {

    private final CommandHandler commandHandler = spy(new CommandHandler());
    private TestFixture subject = TestFixture.create(commandHandler);

    @Test
    void testExpectNoEventsAndNoResult() {
        subject.givenNoPriorActivity().whenCommand(new YieldsNoResult()).expectNoEvents().expectNoResult();
    }

    @Test
    void testExpectResultButNoEvents() {
        subject.givenNoPriorActivity().whenCommand(new YieldsResult()).expectNoEvents().expectResult(isA(String.class));
    }

    @Test
    void testExpectExceptionButNoEvents() {
        subject.givenNoPriorActivity().whenCommand(new YieldsException()).expectNoEvents()
                .expectException(MockException.class);
    }

    @Test
    void testExpectEventButNoResult() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command).expectNoResult();
    }

    @Test
    void testExpectNoEventsLike() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenNoPriorActivity().whenCommand(command).expectNoEventsLike(isA(String.class));
    }

    @Test
    void testExpectResultAndEvent() {
        YieldsEventAndResult command = new YieldsEventAndResult();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command).expectResult(isA(String.class));
    }

    @Test
    void testExpectExceptionAndEvent() {
        YieldsEventAndException command = new YieldsEventAndException();
        subject.givenNoPriorActivity().whenCommand(command).expectOnlyEvents(command)
                .expectException(MockException.class);
    }

    @Test
    void testWithGivenCommandsAndResult() {
        subject.givenCommands(new YieldsNoResult()).whenCommand(new YieldsResult()).expectResult(isA(String.class))
                .expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndNoResult() {
        subject.givenCommands(new YieldsResult()).whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromGiven() {
        subject.givenCommands(new YieldsEventAndResult()).whenCommand(new YieldsNoResult()).expectNoResult()
                .expectNoEvents();
    }

    @Test
    void testWithGivenCommandsAndEventsFromCommand() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenCommands(new YieldsNoResult()).whenCommand(command).expectNoResult().expectEvents(command);
    }

    @Test
    void testWithMultipleGivenCommands() {
        YieldsEventAndNoResult command = new YieldsEventAndNoResult();
        subject.givenCommands(new YieldsNoResult(), new YieldsResult(), command, command).whenCommand(command)
                .expectNoResult().expectOnlyEvents(command);
    }

    @Test
    void testAndGivenCommands() {
        subject.givenCommands(new YieldsResult()).andGivenCommands(new YieldsEventAndNoResult())
                .whenCommand(new YieldsNoResult()).expectNoResult().expectNoEvents();
        InOrder inOrder = inOrder(commandHandler);
        inOrder.verify(commandHandler).handle(new YieldsResult());
        inOrder.verify(commandHandler).handle(new YieldsEventAndNoResult());
        inOrder.verify(commandHandler).handle(new YieldsNoResult());
    }

    @Test
    void testMultiHandler() {
        subject = TestFixture.create(commandHandler, new EventHandler());
        subject.whenCommand(new YieldsEventAndNoResult())
                .expectEvents(new YieldsEventAndNoResult())
                .expectCommands(new YieldsNoResult());
    }

    @Test
    void testGivenCondition() {
        Runnable mockCondition = mock(Runnable.class);
        subject.given(mockCondition).whenCommand(new YieldsNoResult()).verify(() -> verify(mockCondition).run());
    }

    @Test
    void testWhenCondition() {
        Runnable mockCondition = mock(Runnable.class);
        subject.givenNoPriorActivity().when(mockCondition).verify(() -> verify(mockCondition).run());
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
        public void handle(YieldsException command) {
            throw new MockException();
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
        public void handle(Object event) {
            FluxCapacitor.sendCommand(new YieldsNoResult());
        }
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

    private static class FunctionalMockException extends FunctionalException {
    }

}
