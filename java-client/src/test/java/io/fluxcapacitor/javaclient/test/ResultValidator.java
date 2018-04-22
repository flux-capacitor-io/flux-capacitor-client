package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class ResultValidator implements Then {
    private final Object actualResult;
    private final BlockingQueue<Message> resultingEvents;
    private final BlockingQueue<Message> resultingCommands;

    @Override
    public Then expectOnlyEvents(List<?> events) {
        return expectOnlyMessages(events, EVENT, resultingEvents);
    }

    @Override
    public Then expectEvents(List<?> events) {
        return expectMessages(events, EVENT, resultingEvents);
    }

    @Override
    public Then expectOnlyCommands(List<?> commands) {
        return expectOnlyMessages(commands, COMMAND, resultingCommands);
    }

    @Override
    public Then expectCommands(List<?> commands) {
        return expectMessages(commands, COMMAND, resultingCommands);
    }

    protected Then expectMessages(List<?> messages, MessageType messageType, BlockingQueue<Message> resultingMessages) {
        List<Message> expected = asMessages(messages, messageType);
        List<Message> actual = getActualMessages(expected, resultingMessages);
        if (!containsAll(expected, actual)) {
            reportWrongMessages(expected, actual);
        }
        return this;
    }

    protected Then expectOnlyMessages(List<?> messages, MessageType messageType, BlockingQueue<Message> resultingMessages) {
        List<Message> expected = asMessages(messages, messageType);
        List<Message> actual = getActualMessages(expected, resultingMessages);
        if (!equals(expected, actual)) {
            reportWrongMessages(expected, actual);
        }
        return this;
    }

    protected List<Message> getActualMessages(List<Message> expected,
                                              BlockingQueue<Message> resultingMessages) {
        List<Message> result = new ArrayList<>();
        try {
            while (!result.containsAll(expected) && !Thread.interrupted()) {
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

    protected void reportWrongMessages(List<Message> expected, List<Message> actual) {
        String message = format("Published messages did not match.\nExpected: %s\nGot: %s", expected, actual);
        if (actualResult instanceof Throwable) {
            message += "\nA probable cause is an exception that occurred during handling:";
            throw new GivenWhenThenAssertionError(message, (Throwable) actualResult);
        }
        throw new GivenWhenThenAssertionError(message);
    }

    protected boolean equals(List<Message> expected, List<Message> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Iterator<Message> actualIterator = actual.iterator();
        for (Message m : expected) {
            if (!equalsIgnoreNewMetadata(m, actualIterator.next())) {
                return false;
            }
        }
        return true;
    }

    protected boolean containsAll(List<Message> expected, List<Message> actual) {
        for (Message e : expected) {
            if (actual.stream().noneMatch(a -> equalsIgnoreNewMetadata(e, a))) {
                return false;
            }
        }
        return true;
    }

    protected boolean equalsIgnoreNewMetadata(Message expected, Message actual) {
        return expected.getPayload().equals(actual.getPayload()) && actual.getMetadata().entrySet()
                .containsAll(expected.getMetadata().entrySet());
    }

    protected List<Message> asMessages(List<?> events, MessageType type) {
        return events.stream().map(e -> e instanceof Message ? (Message) e : new Message(e, type)).collect(toList());
    }

    @Override
    public Then expectResult(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (actualResult instanceof Throwable) {
            throw new GivenWhenThenAssertionError(format("Handler threw an unexpected exception. Expected: %s",
                                                         description), (Throwable) actualResult);
        }
        if (!resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler returned an unexpected value.\nExpected: %s\nGot: %s",
                                                         description, actualResult));
        }
        return this;
    }

    @Override
    public Then expectException(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (!(actualResult instanceof Throwable)) {
            throw new GivenWhenThenAssertionError(format("Handler returned normally but an exception was expected. Expected: %s. Got: %s",
                                                         description, actualResult));
        }
        if (!resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler returned unexpected value.\nExpected: %s\nGot: %s",
                                                         description, actualResult));
        }
        return this;
    }
}
