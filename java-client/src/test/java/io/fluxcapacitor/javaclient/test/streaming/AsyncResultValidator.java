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

package io.fluxcapacitor.javaclient.test.streaming;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.test.AbstractResultValidator;
import io.fluxcapacitor.javaclient.test.Then;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.Thread.currentThread;

public class AsyncResultValidator extends AbstractResultValidator {
    private final BlockingQueue<Message> resultingEvents;
    private final BlockingQueue<Message> resultingCommands;

    public AsyncResultValidator(Object actualResult,
                                BlockingQueue<Message> resultingEvents,
                                BlockingQueue<Message> resultingCommands) {
        super(actualResult);
        this.resultingEvents = resultingEvents;
        this.resultingCommands = resultingCommands;
    }

    @Override
    public Then expectOnlyEvents(List<?> events) {
        return expectOnlyMessages(events, EVENT, resultingEvents);
    }

    @Override
    public Then expectEvents(List<?> events) {
        return expectMessages(events, EVENT, resultingEvents);
    }

    @Override
    public Then expectNoEventsLike(List<?> events) {
        return expectNoMessagesLike(events, EVENT, resultingEvents);
    }

    @Override
    public Then expectOnlyCommands(List<?> commands) {
        return expectOnlyMessages(commands, COMMAND, resultingCommands);
    }

    @Override
    public Then expectCommands(List<?> commands) {
        return expectMessages(commands, COMMAND, resultingCommands);
    }

    @Override
    public Then expectNoCommandsLike(List<?> commands) {
        return expectNoMessagesLike(commands, COMMAND, resultingCommands);
    }

    protected Then expectMessages(List<?> messages, MessageType messageType, BlockingQueue<Message> resultingMessages) {
        Collection<?> expected = asMessages(messages, messageType);
        Collection<Message> actual = getActualMessages(expected, resultingMessages);
        return expectMessages(expected, actual);
    }

    protected Then expectOnlyMessages(Collection<?> messages, MessageType messageType,
                                      BlockingQueue<Message> resultingMessages) {
        Collection<?> expected = asMessages(messages, messageType);
        Collection<Message> actual = getActualMessages(expected, resultingMessages);
        return expectOnlyMessages(expected, actual);
    }


    protected Then expectNoMessagesLike(Collection<?> messages, MessageType messageType,
                                        BlockingQueue<Message> resultingMessages) {
        Collection<?> expected = asMessages(messages, messageType);
        Collection<Message> actual = getActualMessages(expected, resultingMessages);
        return expectNoMessagesLike(expected, actual);
    }

    protected Collection<Message> getActualMessages(Collection<?> expected, BlockingQueue<Message> resultingMessages) {
        Collection<Message> result = new ArrayList<>();
        try {
            while ((expected.isEmpty() || !containsAll(expected, result)) && !Thread.interrupted()) {
                Message next = resultingMessages.poll(1L, TimeUnit.SECONDS);
                if (next == null) {
                    return result;
                } else {
                    result.add(next);
                }
            }
        } catch (InterruptedException e) {
            currentThread().interrupt();
            return result;
        }
        return result;
    }
}
