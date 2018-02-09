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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class ResultValidator implements Then {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Message trigger;
    private final Object actualResult;
    private final BlockingQueue<Message> resultingEvents;
    private final BlockingQueue<Message> resultingCommands;

    @Override
    public Then expectOnlyEvents(List<?> events) {
        List<Message> expected = asMessages(events, EVENT);
        List<Message> actual = getActualEvents(expected);
        if (!equals(expected, actual)) {
            reportWrongEvents(expected, actual);
        }
        return this;
    }

    @Override
    public Then expectEvents(List<?> events) {
        List<Message> expected = asMessages(events, EVENT);
        List<Message> actual = getActualEvents(expected);
        if (!containsAll(expected, actual)) {
            reportWrongEvents(expected, actual);
        }
        return this;
    }

    protected void reportWrongEvents(List<Message> expected, List<Message> actual) {
        String message = format("Published events did not match.\nExpected: %s\nGot: %s", expected, actual);
        if (actualResult instanceof Throwable) {
            message += "\nA probable cause is an exception that occurred during handling:";
            throw new GivenWhenThenAssertionError(message, (Throwable) actualResult);
        }
        throw new GivenWhenThenAssertionError(message);
    }

    protected List<Message> getActualEvents(List<Message> expected) {
        List<Message> result = new ArrayList<>();
        try {
            while (!result.containsAll(expected) && !Thread.interrupted()) {
                Message next = resultingEvents.poll(1L, TimeUnit.SECONDS);
                if (next == null) {
                    return result;
                } else {
                    result.add(next);
                }
            }
        } catch (InterruptedException e) {
            return result;
        }
        return result;
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
}
