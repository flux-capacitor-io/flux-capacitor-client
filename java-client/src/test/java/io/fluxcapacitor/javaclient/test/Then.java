/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;

/**
 * Interface of the `then` phase of a behavioral given-when-Then<R> test. Here you specify the expected behavior of your
 * `when` phase.
 */
public interface Then<R> {

    /*
        Events
     */

    /**
     * Test if the given events got published.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be tested against published events including
     * any of the Message's metadata. Otherwise, the event is tested against published events using the passed value as
     * payload without additional metadata.
     * <p>
     * An event may also be an instance of {@link Predicate}, hamcrest matcher, or Class. An event may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectEvents(Object... events);

    /**
     * Test if an event got published that matches the given predicate.
     */
    default <T> Then<R> expectEvent(Predicate<T> predicate) {
        return expectEvents(predicate);
    }

    /**
     * Test if the given events are the *only* events that got published.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be tested against published events including
     * any of the Message's metadata. Otherwise, the event is tested against published events using the passed value as
     * payload without additional metadata.
     * <p>
     * An event may also be an instance of {@link Predicate}, hamcrest matcher, or Class. An event may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlyEvents(Object... events);

    /**
     * Assert that the given events did *not* get published.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be tested against published events including
     * any of the Message's metadata. Otherwise, the event is tested against published events using the passed value as
     * payload without additional metadata.
     * <p>
     * An event may also be an instance of {@link Predicate}, hamcrest matcher, or Class. An event may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoEventsLike(Object... events);

    /**
     * Assert that no events got published.
     */
    default Then<R> expectNoEvents() {
        return expectOnlyEvents();
    }

    /*
        Commands
     */

    /**
     * Test if the given commands got published.
     * <p>
     * A command may be an instance of {@link Message} in which case it will be tested against published commands
     * including any of the Message's metadata. Otherwise, the command is tested against published commands using the
     * passed value as payload without additional metadata.
     * <p>
     * A command may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A command may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectCommands(Object... commands);

    /**
     * Test if a command got published that matches the given predicate.
     */
    default <T> Then<R> expectCommand(Predicate<T> predicate) {
        return expectCommands(predicate);
    }

    /**
     * Test if the given commands are the *only* commands that got published.
     * <p>
     * A command may be an instance of {@link Message} in which case it will be tested against published commands
     * including any of the Message's metadata. Otherwise, the command is tested against published commands using the
     * passed value as payload without additional metadata.
     * <p>
     * A command may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A command may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlyCommands(Object... commands);

    /**
     * Assert that the given commands did *not* get published.
     * <p>
     * A command may be an instance of {@link Message} in which case it will be tested against published commands
     * including any of the Message's metadata. Otherwise, the command is tested against published commands using the
     * passed value as payload without additional metadata.
     * <p>
     * A command may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A command may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoCommandsLike(Object... commands);

    /**
     * Assert that no commands got published.
     */
    default Then<R> expectNoCommands() {
        return expectOnlyCommands();
    }
    
    /*
        Queries
     */

    /**
     * Test if the given queries got published.
     * <p>
     * A query may be an instance of {@link Message} in which case it will be tested against published queries including
     * any of the Message's metadata. Otherwise, the query is tested against published queries using the passed value as
     * payload without additional metadata.
     * <p>
     * A query may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A query may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectQueries(Object... queries);

    /**
     * Test if a query got published that matches the given predicate.
     */
    default <T> Then<R> expectQuery(Predicate<T> predicate) {
        return expectQueries(predicate);
    }

    /**
     * Test if the given queries are the *only* queries that got published.
     * <p>
     * A query may be an instance of {@link Message} in which case it will be tested against published queries including
     * any of the Message's metadata. Otherwise, the query is tested against published queries using the passed value as
     * payload without additional metadata.
     * <p>
     * A query may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A query may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlyQueries(Object... queries);

    /**
     * Assert that the given queries did *not* get published.
     * <p>
     * A query may be an instance of {@link Message} in which case it will be tested against published queries including
     * any of the Message's metadata. Otherwise, the query is tested against published queries using the passed value as
     * payload without additional metadata.
     * <p>
     * A query may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A query may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoQueriesLike(Object... queries);

    /**
     * Assert that no queries got published.
     */
    default Then<R> expectNoQueries() {
        return expectOnlyQueries();
    }

    /*
        Web requests
     */

    /**
     * Assert that the given web requests got published.
     * <p>
     * A given web request may be an instance of {@link Message} or {@link WebRequest} in which case it will be tested
     * against published web requests including any of the Message's metadata. Otherwise, the request is tested against
     * published web requests using the passed value as payload without additional metadata.
     * <p>
     * A web request may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A web request may also
     * refer to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/send-mail-request.json".
     */
    Then<R> expectWebRequests(Object... webRequests);

    /**
     * Test if a web request got published that matches the given predicate.
     */
    default Then<R> expectWebRequest(Predicate<WebRequest> predicate) {
        return expectWebRequests(predicate);
    }

    /**
     * Assert that the given values are the *only* web requests that got published.
     * <p>
     * A web request may be an instance of {@link Message} or {@link WebRequest} in which case it will be tested against
     * published web requests including any of the Message's metadata. Otherwise, the web request is tested against
     * published web requests using the passed value as payload without additional metadata.
     * <p>
     * A web request may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A request may also refer
     * to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "send-email-request.json".
     */
    Then<R> expectOnlyWebRequests(Object... webRequests);

    /**
     * Assert that the given web requests did *not* get published.
     * <p>
     * A web request may be an instance of {@link Message} in which case it will be tested against published web
     * requests including any of the Message's metadata. Otherwise, the value is tested against published web requests
     * using the passed value as payload without additional metadata.
     * <p>
     * A web request may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A web request may also
     * refer to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "send-email-request.json".
     */
    Then<R> expectNoWebRequestsLike(Object... webRequests);

    /**
     * Assert that no web requests got published.
     */
    default Then<R> expectNoWebRequests() {
        return expectOnlyWebRequests();
    }

    /*
        Web responses
     */

    /**
     * Assert that the given web responses got published.
     * <p>
     * A given web response may be an instance of {@link Message} or {@link WebResponse} in which case it will be tested
     * against published web responses including any of the Message's metadata. Otherwise, the command is tested against
     * published web responses using the passed value as payload without additional metadata.
     * <p>
     * A web response may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A web response may also
     * refer to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/new-user-response.json".
     */
    Then<R> expectWebResponses(Object... webResponses);

    /**
     * Test if a web response got published that matches the given predicate.
     */
    default Then<R> expectWebResponse(Predicate<WebResponse> predicate) {
        return expectWebResponses(predicate);
    }

    /**
     * Assert that the given values are the *only* web responses that got published.
     * <p>
     * A web response may be an instance of {@link Message} or {@link WebResponse} in which case it will be tested
     * against published web responses including any of the Message's metadata. Otherwise, the web response is tested
     * against published web responses using the passed value as payload without additional metadata.
     * <p>
     * A web response may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A command may also refer
     * to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "new-user-response.json".
     */
    Then<R> expectOnlyWebResponses(Object... webResponses);

    /**
     * Assert that the given web responses did *not* get published.
     * <p>
     * A web response may be an instance of {@link Message} in which case it will be tested against published web
     * responses including any of the Message's metadata. Otherwise, the value is tested against published web responses
     * using the passed value as payload without additional metadata.
     * <p>
     * A web response may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A web response may also
     * refer to a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "new-user-response.json".
     */
    Then<R> expectNoWebResponsesLike(Object... webResponses);

    /**
     * Assert that no web responses got published.
     */
    default Then<R> expectNoWebResponses() {
        return expectOnlyWebResponses();
    }

    /*
        Schedules
     */

    /**
     * Test if the given schedules got published.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNewSchedules(Object... schedules);

    /**
     * Test if a schedule got published that matches the given predicate.
     */
    default Then<R> expectNewSchedule(Predicate<Schedule> predicate) {
        return expectNewSchedules(predicate);
    }

    /**
     * Test if the given schedules are the *only* new schedules that got published.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlyNewSchedules(Object... schedules);

    /**
     * Assert that the given schedules did not get published.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoNewSchedulesLike(Object... schedules);

    /**
     * Assert that no new schedules got published.
     */
    default Then<R> expectNoNewSchedules() {
        return expectOnlyNewSchedules();
    }

    /**
     * Assert that the given schedules are still active.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectSchedules(Object... schedules);

    /**
     * Test if a schedule is still active that matches the given predicate.
     */
    default Then<R> expectSchedule(Predicate<Schedule> predicate) {
        return expectSchedules(predicate);
    }

    /**
     * Test if the given schedules are the *only* schedules that are still active.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlySchedules(Object... schedules);

    /**
     * Assert that the given schedules are not active.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be tested against published schedules
     * including any of the Message's metadata. Otherwise, the schedule is tested against published schedules using the
     * passed value as payload without additional metadata. If the schedule is an instance of a {@link Schedule} the
     * deadline of the expected schedule will also be tested against published schedules.
     * <p>
     * A schedule may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A schedule may also refer to
     * a json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoSchedulesLike(Object... schedules);

    /**
     * Assert that there are no running schedules.
     */
    default Then<R> expectNoSchedules() {
        return expectOnlySchedules();
    }

    /*
        Normal result
     */

    /**
     * Test if the actual result of the test fixture matches the given result.
     * <p>
     * The given result may be a {@link Predicate}, hamcrest matcher, or Class. The result may also refer to a json
     * resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json". In all other cases, the test fixture result will be compared to the given result by
     * checking if the results are equal via {@link Objects#equals(Object, Object)}.
     */
    Then<R> expectResult(Object result);

    /**
     * Test if the actual result of the test fixture is an instance of the given class.
     */
    default <R2 extends R> Then<R2> expectResult(@NonNull Class<? extends R2> resultClass) {
        return this.expectResult(r -> r instanceof Class<?> ? r.equals(resultClass) : resultClass.isInstance(r),
                                 format("an instance of %s", resultClass.getSimpleName()));
    }

    /**
     * Test if the actual result of the test fixture matches the given predicate.
     */
    default <R2 extends R> Then<R2> expectResult(Predicate<R2> predicate) {
        return expectResult(predicate, "Predicate matcher");
    }

    /**
     * Assert that the actual result of the test fixture matches the given predicate summarized by given description.
     */
    <R2 extends R> Then<R2> expectResult(Predicate<R2> predicate, String description);

    /**
     * Assert that the actual result of the test fixture is a {@link Message} and matches the given predicate.
     */
    default <M extends Message> Then<R> expectResultMessage(Predicate<M> messagePredicate) {
        return expectResultMessage(messagePredicate, "Predicate matcher");
    }

    /**
     * ssert that the actual result of the test fixture is a {@link Message} and matches the given predicate summarized
     * by given description.
     */
    <M extends Message> Then<R> expectResultMessage(Predicate<M> messagePredicate, String description);

    /**
     * Assert that the result of the test fixture is non-null.
     */
    default Then<R> expectNonNullResult() {
        return expectResult(Objects::nonNull);
    }

    /**
     * Assert that the test fixture did not yield a result or exception (i.e. actual result is {@code null}).
     */
    default Then<R> expectNoResult() {
        return expectResult((Object) null);
    }

    /**
     * Assert that the test fixture did *not* yield a result matching the given result.
     * <p>
     * The given result may be a {@link Predicate}, hamcrest matcher, or Class. The result may also refer to a json
     * resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json". In all other cases, the test fixture result will be compared to the given result by
     * checking if the results are equal via {@link Objects#equals(Object, Object)}.
     */
    Then<R> expectNoResultLike(Object result);

    /**
     * Assert that the actual result of the test fixture is a {@link Collection} or {@link Map} with containing values
     * that match the given results.
     * <p>
     * A given result may be a {@link Predicate}, hamcrest matcher, or Class. The result may also refer to a json
     * resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json". In all other cases, the test fixture result will be compared to the given result by
     * checking if the results are equal via {@link Objects#equals(Object, Object)}.
     */
    @SuppressWarnings("unchecked")
    <T> Then<R> expectResultContaining(T... results);

    /**
     * Maps the expected result of the test fixture to something else using the given {@code resultMapper}. This is
     * typically used to simplify result checks.
     */
    <MR> Then<MR> mapResult(Function<? super R, ? extends MR> resultMapper);

    /*
        Exceptions
     */

    /**
     * Assert that the test fixture completed exceptionally and that the exception matches the given result.
     * <p>
     * The given result may be a {@link Predicate}, hamcrest matcher, or Class. The result may also refer to a json
     * resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json". In all other cases, the test fixture result will be compared to the given result by
     * checking if the results are equal via {@link Objects#equals(Object, Object)}.
     */
    Then<R> expectExceptionalResult(Object expectedException);

    /**
     * Assert that the test fixture completed exceptionally.
     */
    default Then<R> expectExceptionalResult() {
        return expectExceptionalResult(Objects::nonNull);
    }

    /**
     * Assert that the test fixture completed exceptionally and that the exception is an instance of the given class.
     */
    default Then<R> expectExceptionalResult(@NonNull Class<? extends Throwable> exceptionClass) {
        return expectExceptionalResult(exceptionClass::isInstance,
                                       format("an instance of %s", exceptionClass.getSimpleName()));
    }

    /**
     * Assert that the test fixture completed exceptionally and that the exception matches the given predicate.
     */
    default <T extends Throwable> Then<R> expectExceptionalResult(Predicate<T> predicate) {
        return expectExceptionalResult(predicate, "Predicate matcher");
    }

    /**
     * Assert that the test fixture completed exceptionally and that the exception matches the given predicate described
     * by the given error message.
     */
    <T extends Throwable> Then<R> expectExceptionalResult(Predicate<T> predicate, String errorMessage);

    /**
     * Assert that the test fixture completed without exceptional result.
     */
    default Then<R> expectSuccessfulResult() {
        return expectResult(r -> !(r instanceof Throwable));
    }

    /**
     * Assert that the test fixture completed without exceptional result and also provides a quick way to cast the
     * fixture result to have the type of the given argument {@link R2}.
     */
    @SuppressWarnings("unchecked")
    default <R2 extends R> Then<R2> expectResult() {
        return (Then<R2>) expectSuccessfulResult();
    }

    /*
        Errors
     */

    /**
     * Assert that the test fixture handler yielded an exception anywhere. This error does not need to be the returned
     * result of the action in the `when` phase. To assert that use methods that test for exceptional results.
     * <p>
     * The error may be a {@link Predicate}, hamcrest matcher, or Class. The error may also refer to a json resource in
     * the class path of the unit test by passing a string ending in `.json`, e.g. "expected/create-user.json". In all
     * other cases, the actual error will be compared to the given error by checking if the errors are equal via
     * {@link Objects#equals(Object, Object)}.
     */
    Then<R> expectError(Object expectedError);

    /**
     * Assert that the test fixture handler yielded an exception anywhere and that the exception matches the given
     * predicate. This error does not need to be the returned result of the action in the `when` phase. To assert that
     * use methods that test for exceptional results.
     */
    default <T extends Throwable> Then<R> expectError(Predicate<T> predicate) {
        return expectError(predicate, "Predicate matcher");
    }

    /**
     * Assert that the test fixture handler yielded an exception anywhere. This error does not need to be the returned
     * result of the action in the `when` phase. To assert that use methods that test for exceptional results.
     */
    default Then<R> expectError() {
        return expectError(Objects::nonNull);
    }

    /**
     * Assert that the test fixture handler yielded an exception anywhere and that the exception is an instance of given
     * error class. This error does not need to be the returned result of the action in the `when` phase. To assert that
     * use methods that test for exceptional results.
     */
    default Then<R> expectError(@NonNull Class<? extends Throwable> errorClass) {
        return expectError(errorClass::isInstance, format("an instance of %s", errorClass.getSimpleName()));
    }

    /**
     * Assert that the test fixture handler yielded an exception anywhere and that the exception matches the given
     * predicate described by given error message. This error does not need to be the returned result of the action in
     * the `when` phase. To assert that use methods that test for exceptional results.
     */
    <T extends Throwable> Then<R> expectError(Predicate<T> predicate, String errorMessage);

    /**
     * Assert that there has not been any error produced by any handler.
     */
    Then<R> expectNoErrors();

    /*
        Metrics
     */

    /**
     * Test if the given metrics got published.
     * <p>
     * An metric may be an instance of {@link Message} in which case it will be tested against published metrics
     * including any of the Message's metadata. Otherwise, the metric is tested against published metrics using the
     * passed value as payload without additional metadata.
     * <p>
     * A metric may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A metric may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectMetrics(Object... metrics);

    /**
     * Test if a metric got published that matches the given predicate.
     */
    default <T> Then<R> expectMetric(Predicate<T> predicate) {
        return expectMetrics(predicate);
    }

    /**
     * Test if the given metrics are the *only* events that got published.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be tested against published metrics
     * including any of the Message's metadata. Otherwise, the metric is tested against published events using the
     * passed value as payload without additional metadata.
     * <p>
     * A metric may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A metric may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectOnlyMetrics(Object... metrics);

    /**
     * Assert that the given metrics did *not* get published.
     * <p>
     * A metric may be an instance of {@link Message} in which case it will be tested against published metrics
     * including any of the Message's metadata. Otherwise, the metric is tested against published metrics using the
     * passed value as payload without additional metadata.
     * <p>
     * A metric may also be an instance of {@link Predicate}, hamcrest matcher, or Class. A metric may also refer to a
     * json resource in the class path of the unit test by passing a string ending in `.json`, e.g.
     * "expected/create-user.json".
     */
    Then<R> expectNoMetricsLike(Object... metrics);

    /**
     * Assert that no metrics got published.
     */
    default Then<R> expectNoMetrics() {
        return expectOnlyMetrics();
    }

    /*
        Other
     */

    /**
     * Assert that the test fixture is in the correct state after the `when` phase. You can e.g. use this to verify that
     * mocked methods were invoked correctly.
     */
    Then<R> expectThat(Consumer<FluxCapacitor> check);

    /**
     * Assert that the test fixture is in the correct state after the `when` phase using the given predicate.
     */
    Then<R> expectTrue(Predicate<FluxCapacitor> check);

    /**
     * Assert that the test fixture is in the correct state after the `when` phase by checking that the given predicate
     * returns false.
     */
    default Then<R> expectFalse(Predicate<FluxCapacitor> check) {
        return expectTrue(check.negate());
    }

    /*
        And then
     */

    Given andThen();

}
