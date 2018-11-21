package io.fluxcapacitor.javaclient.test;

import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;

public interface Then {

    default Then expectOnlyEvents(Object... events) {
        return expectOnlyEvents(Arrays.asList(events));
    }

    default Then expectEvents(Object... events) {
        return expectEvents(Arrays.asList(events));
    }

    default Then expectNotTheseEvents(Object... events) {
        return expectNotTheseEvents(Arrays.asList(events));
    }

    default Then expectNoEvents() {
        return expectOnlyEvents();
    }

    Then expectOnlyEvents(List<?> events);

    Then expectEvents(List<?> events);

    Then expectNotTheseEvents(List<?> events);

    default Then expectOnlyCommands(Object... commands) {
        return expectOnlyCommands(Arrays.asList(commands));
    }

    default Then expectCommands(Object... commands) {
        return expectCommands(Arrays.asList(commands));
    }

    default Then expectNotTheseCommands(Object... commands) {
        return expectNotTheseCommands(Arrays.asList(commands));
    }

    default Then expectNoCommands() {
        return expectOnlyCommands();
    }

    Then expectOnlyCommands(List<?> commands);

    Then expectCommands(List<?> commands);

    Then expectNotTheseCommands(List<?> commands);

    default Then expectResult(Object result) {
        if (result == null) {
            return expectResult(nullValue());
        }
        return expectResult(equalTo(result));
    }

    Then expectResult(Matcher<?> resultMatcher);

    Then expectNotThisResult(Matcher<?> resultMatcher);

    Then expectException(Matcher<?> resultMatcher);

    default Then expectException(Class<? extends Throwable> exceptionClass) {
        return expectException(isA(exceptionClass));
    }

    default Then expectNoResult() {
        return expectResult(nullValue());
    }
}
