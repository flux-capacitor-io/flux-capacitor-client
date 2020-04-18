package io.fluxcapacitor.javaclient.test;

import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.isA;

public interface Then {

    /*
        Events
     */

    default Then expectOnlyEvents(Object... events) {
        return expectOnlyEvents(Arrays.asList(events));
    }

    default Then expectEvents(Object... events) {
        return expectEvents(Arrays.asList(events));
    }

    default Then expectNoEventsLike(Object... events) {
        return expectNoEventsLike(Arrays.asList(events));
    }

    default Then expectNoEvents() {
        return expectOnlyEvents();
    }

    Then expectOnlyEvents(List<?> events);

    Then expectEvents(List<?> events);

    Then expectNoEventsLike(List<?> events);

    /*
        Commands
     */

    default Then expectOnlyCommands(Object... commands) {
        return expectOnlyCommands(Arrays.asList(commands));
    }

    default Then expectCommands(Object... commands) {
        return expectCommands(Arrays.asList(commands));
    }

    default Then expectNoCommandsLike(Object... commands) {
        return expectNoCommandsLike(Arrays.asList(commands));
    }

    default Then expectNoCommands() {
        return expectOnlyCommands();
    }

    Then expectOnlyCommands(List<?> commands);

    Then expectCommands(List<?> commands);

    Then expectNoCommandsLike(List<?> commands);

    /*
        Schedules
     */

    default Then expectOnlySchedules(Object... schedules) {
        return expectOnlySchedules(Arrays.asList(schedules));
    }

    default Then expectSchedules(Object... schedules) {
        return expectSchedules(Arrays.asList(schedules));
    }

    default Then expectNoSchedulesLike(Object... schedules) {
        return expectNoSchedulesLike(Arrays.asList(schedules));
    }

    default Then expectNoSchedules() {
        return expectOnlySchedules();
    }

    Then expectOnlySchedules(List<?> schedules);

    Then expectSchedules(List<?> schedules);

    Then expectNoSchedulesLike(List<?> schedules);

    /*
        Result
     */

    Then expectResult(Object result);

    default Then expectException(Class<? extends Throwable> exceptionClass) {
        return expectException(isA(exceptionClass));
    }

    Then expectException(Matcher<?> resultMatcher);

    default Then expectNoResult() {
        return expectResult(null);
    }

    Then expectNoResultLike(Object result);

    /*
        External process
     */

    Then verify(Runnable check);

}
