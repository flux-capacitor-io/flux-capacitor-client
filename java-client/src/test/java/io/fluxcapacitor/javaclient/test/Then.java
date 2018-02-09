package io.fluxcapacitor.javaclient.test;

import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

public interface Then {

    default Then expectOnlyEvents(Object... events) {
        return expectOnlyEvents(Arrays.asList(events));
    }

    default Then expectEvents(Object... events) {
        return expectEvents(Arrays.asList(events));
    }

    default Then expectNoEvents() {
        return expectOnlyEvents();
    }

    Then expectOnlyEvents(List<?> events);

    Then expectEvents(List<?> events);

    default Then expectResult(Object result) {
        if (result == null) {
            return expectResult(nullValue());
        }
        return expectResult(equalTo(result));
    }

    Then expectResult(Matcher<?> resultMatcher);

    default Then expectNoResult() {
        return expectResult(nullValue());
    }
}
