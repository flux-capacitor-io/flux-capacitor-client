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
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.String.format;

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
        Normal result
     */

    Then expectResult(Object result);

    default Then expectResult(@NonNull Class<?> resultClass) {
        return this.expectResult(r -> r instanceof Class<?> ? r.equals(resultClass) : resultClass.isInstance(r),
                                 format("an instance of %s", resultClass.getSimpleName()));
    }

    default <T> Then expectResult(Predicate<T> predicate) {
        return expectResult(predicate, "Predicate matcher");
    }

    <T> Then expectResult(Predicate<T> predicate, String description);

    default Then expectNoResult() {
        return expectResult((Object) null);
    }

    Then expectNoResultLike(Object result);

    /*
        Exceptional result
     */

    Then expectException(Object expectedException);

    default Then expectException() {
        return expectException(Objects::nonNull);
    }

    default Then expectIllegalCommandException() {
        return expectException(e -> e.getClass().getSimpleName().equals("IllegalCommandException"),
                               "an instance of IllegalCommandException");
    }

    default Then expectValidationException() {
        return expectException(ValidationException.class);
    }

    default Then expectAuthenticationException() {
        return expectException(e -> e instanceof UnauthenticatedException || e instanceof UnauthorizedException,
                               format("an instance of %s or %s", UnauthenticatedException.class.getSimpleName(),
                                      UnauthorizedException.class.getSimpleName()));
    }

    default Then expectException(@NonNull Class<? extends Throwable> exceptionClass) {
        return expectException(exceptionClass::isInstance, format("an instance of %s", exceptionClass.getSimpleName()));
    }

    default <T extends Throwable> Then expectException(Predicate<T> predicate) {
        return expectException(predicate, "Predicate matcher");
    }

    <T extends Throwable> Then expectException(Predicate<T> predicate, String errorMessage);

    default Then expectNoException() {
        return expectResult(r -> !(r instanceof Throwable));
    }

    /*
        External process
     */

    Then expectThat(Consumer<FluxCapacitor> check);

    Then expectTrue(Predicate<FluxCapacitor> check);

    default Then expectFalse(Predicate<FluxCapacitor> check) {
        return expectTrue(check.negate());
    }

}
