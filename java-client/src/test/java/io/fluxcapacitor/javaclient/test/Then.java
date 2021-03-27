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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsNot;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.fluxcapacitor.javaclient.test.GivenWhenThenUtils.toMatcher;
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
        Documents
     */

    Then expectOnlyDocuments(List<?> documents);

    Then expectDocuments(List<?> documents);

    Then expectNoDocumentsLike(List<?> documents);

    /*
        Result
     */

    Then expectResult(Object result);

    default <T> Then expectResult(Predicate<T> predicate) {
        return predicate == null ? expectResult((Object) null) : expectResult(toMatcher(predicate));
    }

    default Then expectException() {
        return expectException(isA(Throwable.class));
    }

    default Then expectException(Class<? extends Throwable> exceptionClass) {
        return expectException(isA(exceptionClass));
    }

    default <T extends Throwable> Then expectException(Predicate<T> predicate) {
        return expectException(toMatcher(predicate));
    }

    Then expectException(Matcher<?> resultMatcher);

    default Then expectNoResult() {
        return expectResult((Object) null);
    }

    default Then expectNoException() {
        return expectResult(new IsNot<>(isA(Throwable.class)));
    }

    Then expectNoResultLike(Object result);

    /*
        External process
     */

    Then expectThat(Consumer<FluxCapacitor> check);

}
