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

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.String.format;

public interface Then {

    /*
        Events
     */

    Then expectOnlyEvents(Object... events);

    Then expectEvents(Object... events);

    Then expectNoEventsLike(Object... events);

    default Then expectNoEvents() {
        return expectOnlyEvents();
    }

    /*
        Commands
     */

    Then expectOnlyCommands(Object... commands);

    Then expectCommands(Object... commands);

    Then expectNoCommandsLike(Object... commands);

    default Then expectNoCommands() {
        return expectOnlyCommands();
    }

    /*
        Schedules
     */

    Then expectOnlySchedules(Object... schedules);

    Then expectSchedules(Object... schedules);

    Then expectNoSchedulesLike(Object... schedules);

    default Then expectNoSchedules() {
        return expectOnlySchedules();
    }

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

    @SuppressWarnings("unchecked")
    <T> Then expectResultContaining(T... results);

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
        Other
     */

    Then expectThat(Consumer<FluxCapacitor> check);

    Then expectTrue(Predicate<FluxCapacitor> check);

    default Then expectFalse(Predicate<FluxCapacitor> check) {
        return expectTrue(check.negate());
    }

}
