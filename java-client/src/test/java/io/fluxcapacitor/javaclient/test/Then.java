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

import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.ThrowingPredicate;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.ObjectUtils.run;
import static java.lang.String.format;

/**
 * Defines the {@code then} phase of a behavioral Given-When-Then test using a {@link TestFixture}.
 * <p>
 * Use this interface to assert and validate the outcomes of the {@code when} phase, such as:
 * <ul>
 *     <li>Which messages were published (commands, events, queries, web requests/responses, etc.)</li>
 *     <li>The result or exception returned from executing the behavior</li>
 *     <li>Side effects such as errors, metrics, or schedules</li>
 *     <li>Arbitrary conditions or state in the {@link FluxCapacitor} runtime</li>
 * </ul>
 * <p>
 * This interface provides fluent, expressive assertions that support a wide range of matchers:
 * <ul>
 *     <li>Plain objects (compared via {@link java.util.Objects#equals})</li>
 *     <li>{@link java.util.function.Predicate} and Hamcrest matchers</li>
 *     <li>{@link Class} instances (type matching)</li>
 *     <li>Strings ending in {@code .json}, which are interpreted as resource files to load and compare</li>
 * </ul>
 * <p>
 * Resource-based comparisons support rich object declarations with {@code @class} and {@code @extends} for type resolution
 * and inheritance. See {@link io.fluxcapacitor.common.serialization.JsonUtils} for details.
 * <p>
 * Method groups include:
 * <ul>
 *     <li>{@code expectXxx(...)} — asserts that one or more messages/results/errors did occur</li>
 *     <li>{@code expectOnlyXxx(...)} — asserts that only the specified items occurred</li>
 *     <li>{@code expectNoXxxLike(...)} — asserts that none of the specified items occurred</li>
 *     <li>{@code expectNoXxx()} — shorthand to assert that nothing was published in a category</li>
 *     <li>{@code expectResult(...)} / {@code expectExceptionalResult(...)} — asserts outcomes</li>
 *     <li>{@code verifyXxx(...)} — alternative form using imperative assertions</li>
 *     <li>{@code expectThat(...)} — free-form assertion on FluxCapacitor state</li>
 *     <li>{@code andThen()} — continue to the next scenario phase</li>
 * </ul>
 *
 * @param <R> The type of result expected from the {@code when} phase.
 */
public interface Then<R> {

    /*
        Events
     */

    /**
     * Asserts that one or more events were published during the {@code when} phase.
     * <p>
     * Events can be specified as:
     * <ul>
     *   <li>{@link Message} instances — compared with metadata</li>
     *   <li>POJOs — compared by payload only</li>
     *   <li>{@link Predicate}, Hamcrest matchers, or {@link Class} — matched against published payloads</li>
     *   <li>{@code .json} file paths — deserialized and matched via {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     * Matching is performed using equals or matcher logic depending on the type.
     */
    Then<R> expectEvents(Object... events);

    /**
     * Shorthand for asserting that at least one event was published that matches the given {@link Predicate}.
     *
     * @param predicate matcher to apply to published event payloads
     */
    default <T> Then<R> expectEvent(ThrowingPredicate<T> predicate) {
        return expectEvents(predicate.asPredicate());
    }

    /**
     * Asserts that the specified events are the only ones published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectEvents(...)} followed by asserting that no other events were published.
     */
    Then<R> expectOnlyEvents(Object... events);

    /**
     * Asserts that none of the specified events were published.
     * <p>
     * This is a negative matcher that verifies exclusion.
     */
    Then<R> expectNoEventsLike(Object... events);

    /**
     * Asserts that no events were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectOnlyEvents()}.
     */
    default Then<R> expectNoEvents() {
        return expectOnlyEvents();
    }

    /*
        Commands
     */

    /**
     * Asserts that one or more commands were published during the {@code when} phase.
     * <p>
     * Supported values for each command include:
     * <ul>
     *   <li>{@link Message} instances — matched including metadata</li>
     *   <li>Plain objects — matched by payload only</li>
     *   <li>{@link Predicate}, Hamcrest matchers, or {@link Class} — type or condition-based matching</li>
     *   <li>Paths ending in {@code .json} — matched by deserializing a JSON resource file</li>
     * </ul>
     * See {@link io.fluxcapacitor.common.serialization.JsonUtils} for details about JSON file resolution and inheritance.
     */
    Then<R> expectCommands(Object... commands);

    /**
     * Shorthand for asserting that at least one published command matches the given {@link Predicate}.
     *
     * @param predicate Predicate to apply to the published command payloads
     */
    default <T> Then<R> expectCommand(ThrowingPredicate<T> predicate) {
        return expectCommands(predicate.asPredicate());
    }

    /**
     * Asserts that the given commands are the only commands published during the {@code when} phase.
     * <p>
     * Combines {@link #expectCommands(Object...)} with a check that no other commands were published.
     */
    Then<R> expectOnlyCommands(Object... commands);

    /**
     * Asserts that none of the specified commands were published.
     * <p>
     * Useful for excluding specific commands by payload, type, or match condition.
     */
    Then<R> expectNoCommandsLike(Object... commands);

    /**
     * Asserts that no commands were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectOnlyCommands()}.
     */
    default Then<R> expectNoCommands() {
        return expectOnlyCommands();
    }
    
    /*
        Custom Topics
     */

    /**
     * Asserts that one or more messages were published to the specified custom {@code topic}.
     * <p>
     * Each request may be:
     * <ul>
     *   <li>A {@link Message} — matched including metadata</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — used to match payloads dynamically</li>
     *   <li>A {@code .json} resource path — loaded and deserialized via {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     *
     * @param topic    the topic to check for published messages
     * @param requests the expected messages published to the topic
     */
    Then<R> expectCustom(String topic, Object... requests);

    /**
     * Asserts that a message was published to the specified custom {@code topic} and that it matches the given
     * predicate.
     *
     * @param topic     the topic to check
     * @param predicate a predicate to apply to published payloads
     */
    default <T> Then<R> expectCustom(String topic, ThrowingPredicate<T> predicate) {
        return expectCustom(topic, predicate.asPredicate());
    }

    /**
     * Asserts that the specified requests are the only messages published to the given custom {@code topic}.
     * <p>
     * No additional messages should be published on the topic beyond those provided.
     *
     * @param topic    the topic to check
     * @param requests the expected messages
     */
    Then<R> expectOnlyCustom(String topic, Object... requests);

    /**
     * Asserts that none of the given requests were published to the specified custom {@code topic}.
     *
     * @param topic    the topic to check
     * @param requests the messages that must not appear on the topic
     */
    Then<R> expectNoCustomLike(String topic, Object... requests);

    /**
     * Asserts that no messages were published to the specified custom {@code topic}.
     * <p>
     * Equivalent to {@code expectOnlyCustom(topic)}.
     *
     * @param topic the topic to check
     */
    default Then<R> expectNoCustom(String topic) {
        return expectOnlyCustom(topic);
    }
    
    /*
        Queries
     */

    /**
     * Asserts that one or more queries were published during the {@code when} phase.
     * <p>
     * Each query can be:
     * <ul>
     *   <li>A {@link Message} — matched including metadata</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — for type or condition matching</li>
     *   <li>A {@code .json} file path — matched via deserialized JSON resource using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     *
     * @param queries the expected queries
     */
    Then<R> expectQueries(Object... queries);

    /**
     * Shorthand for asserting that at least one published query matches the given predicate.
     *
     * @param predicate matcher to apply to query payloads
     */
    default <T> Then<R> expectQuery(ThrowingPredicate<T> predicate) {
        return expectQueries(predicate.asPredicate());
    }

    /**
     * Asserts that only the specified queries were published during the {@code when} phase.
     * <p>
     * Combines {@link #expectQueries(Object...)} with a check that no additional queries were published.
     *
     * @param queries the only queries that should have been published
     */
    Then<R> expectOnlyQueries(Object... queries);

    /**
     * Asserts that none of the specified queries were published.
     *
     * @param queries queries that must not have been published
     */
    Then<R> expectNoQueriesLike(Object... queries);

    /**
     * Asserts that no queries were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectOnlyQueries()}.
     */
    default Then<R> expectNoQueries() {
        return expectOnlyQueries();
    }

    /*
        Web Requests
     */

    /**
     * Asserts that one or more {@link WebRequest}s were published during the {@code when} phase.
     * <p>
     * Each request can be:
     * <ul>
     *   <li>A {@link WebRequest} or {@link Message} — matched including metadata</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — for dynamic matching</li>
     *   <li>A {@code .json} resource path — deserialized and matched using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     *
     * @param webRequests the expected web requests
     */
    Then<R> expectWebRequests(Object... webRequests);

    /**
     * Shorthand for asserting that at least one published web request matches the given predicate.
     *
     * @param predicate predicate to apply to web request payloads
     */
    default Then<R> expectWebRequest(ThrowingPredicate<WebRequest> predicate) {
        return expectWebRequests(predicate.asPredicate());
    }

    /**
     * Asserts that only the specified {@link WebRequest}s were published during the {@code when} phase.
     *
     * @param webRequests the only expected web requests
     */
    Then<R> expectOnlyWebRequests(Object... webRequests);

    /**
     * Asserts that none of the specified {@link WebRequest}s were published.
     *
     * @param webRequests the disallowed web requests
     */
    Then<R> expectNoWebRequestsLike(Object... webRequests);

    /**
     * Asserts that no {@link WebRequest}s were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectOnlyWebRequests()}.
     */
    default Then<R> expectNoWebRequests() {
        return expectOnlyWebRequests();
    }

    /*
        Web Responses
     */

    /**
     * Asserts that one or more {@link WebResponse}s were published during the {@code when} phase.
     * <p>
     * Each web response can be:
     * <ul>
     *   <li>A {@link WebResponse} or {@link Message} — matched including metadata</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — used to match dynamically</li>
     *   <li>A {@code .json} resource path — deserialized via {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     *
     * @param webResponses the expected responses
     */
    Then<R> expectWebResponses(Object... webResponses);

    /**
     * Shorthand for asserting that at least one published web response matches the given predicate.
     *
     * @param predicate matcher to apply to web response payloads
     */
    default Then<R> expectWebResponse(ThrowingPredicate<WebResponse> predicate) {
        return expectWebResponses(predicate.asPredicate());
    }

    /**
     * Asserts that only the specified web responses were published.
     *
     * @param webResponses the only allowed web responses
     */
    Then<R> expectOnlyWebResponses(Object... webResponses);

    /**
     * Asserts that none of the specified web responses were published.
     *
     * @param webResponses the disallowed web responses
     */
    Then<R> expectNoWebResponsesLike(Object... webResponses);

    /**
     * Asserts that no web responses were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectOnlyWebResponses()}.
     */
    default Then<R> expectNoWebResponses() {
        return expectOnlyWebResponses();
    }

    /*
        Schedules
     */

    /**
     * Asserts that one or more new schedules were published during the {@code when} phase.
     * <p>
     * Each schedule can be:
     * <ul>
     *   <li>A {@link Schedule} or {@link Message} — matched by payload and (if applicable) deadline</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — for flexible payload matching</li>
     *   <li>A {@code .json} file — loaded and deserialized using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     */
    Then<R> expectNewSchedules(Object... schedules);

    /**
     * Shorthand for asserting that at least one new schedule was published and matches the given predicate.
     *
     * @param predicate a condition to apply to the published {@link Schedule}
     */
    default Then<R> expectNewSchedule(ThrowingPredicate<Schedule> predicate) {
        return expectNewSchedules(predicate.asPredicate());
    }

    /**
     * Asserts that only the specified schedules were newly published during the {@code when} phase.
     * <p>
     * No additional schedules should have been created.
     */
    Then<R> expectOnlyNewSchedules(Object... schedules);

    /**
     * Asserts that none of the specified new schedules were published.
     *
     * @param schedules the disallowed schedules
     */
    Then<R> expectNoNewSchedulesLike(Object... schedules);

    /**
     * Asserts that no new schedules were published.
     * <p>
     * Equivalent to {@code expectOnlyNewSchedules()}.
     */
    default Then<R> expectNoNewSchedules() {
        return expectOnlyNewSchedules();
    }

    /**
     * Asserts that the given schedules are currently still active.
     * <p>
     * This checks the system's internal state after the {@code when} phase.
     */
    Then<R> expectSchedules(Object... schedules);

    /**
     * Shorthand for asserting that an active schedule matches the given predicate.
     *
     * @param predicate matcher to apply to active schedules
     */
    default Then<R> expectSchedule(ThrowingPredicate<Schedule> predicate) {
        return expectSchedules(predicate.asPredicate());
    }

    /**
     * Asserts that only the given schedules are currently active.
     */
    Then<R> expectOnlySchedules(Object... schedules);

    /**
     * Asserts that none of the specified schedules are currently active.
     */
    Then<R> expectNoSchedulesLike(Object... schedules);

    /**
     * Asserts that there are no schedules currently active.
     * <p>
     * Equivalent to {@code expectOnlySchedules()}.
     */
    default Then<R> expectNoSchedules() {
        return expectOnlySchedules();
    }

    /*
        Normal Result
     */

    /**
     * Asserts that the result produced during the {@code when} phase matches the given value.
     * <p>
     * The expected value may be:
     * <ul>
     *   <li>A POJO — matched using {@link Objects#equals(Object, Object)}</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class} — used for dynamic or type-based checks</li>
     *   <li>A path to a {@code .json} resource — deserialized and matched using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     */
    Then<R> expectResult(Object result);

    /**
     * Asserts that the result is an instance of the specified class. Also casts the result to that class in the
     * returned {@code Then} step.
     */
    default <R2 extends R> Then<R2> expectResult(@NonNull Class<? extends R2> resultClass) {
        return this.expectResult(r -> r instanceof Class<?> ? r.equals(resultClass) : resultClass.isInstance(r),
                                 format("an instance of %s", resultClass.getSimpleName()));
    }

    /**
     * Asserts that the result matches the provided predicate.
     */
    default <R2 extends R> Then<R2> expectResult(ThrowingPredicate<R2> predicate) {
        return expectResult(predicate, "Predicate matcher");
    }

    /**
     * Executes an imperative check against the result using a verifier function.
     * <p>
     * If the verifier throws an exception, the test fails.
     */
    default <R2 extends R> Then<R2> verifyResult(ThrowingConsumer<R2> verifier) {
        return expectResult(r -> {
            try {
                verifier.accept(r);
                return true;
            } catch (Throwable e) {
                run(() -> {
                    throw e;
                });
                return false;
            }
        }, "Custom verifier");
    }

    /**
     * Asserts that the result matches the given predicate and provides a descriptive label for failures.
     */
    <R2 extends R> Then<R2> expectResult(ThrowingPredicate<R2> predicate, String description);

    /**
     * Asserts that the result is a {@link Message} that matches the given predicate.
     */
    default <M extends Message> Then<R> expectResultMessage(ThrowingPredicate<M> messagePredicate) {
        return expectResultMessage(messagePredicate, "Predicate matcher");
    }

    /**
     * Asserts that the result is a {@link Message} and matches the given predicate and label.
     */
    <M extends Message> Then<R> expectResultMessage(ThrowingPredicate<M> messagePredicate, String description);

    /**
     * Asserts that the result is a {@link WebResponse} that matches the given predicate.
     */
    default Then<R> expectWebResult(ThrowingPredicate<WebResponse> messagePredicate) {
        return expectResultMessage(messagePredicate, "Predicate matcher");
    }

    /**
     * Verifies the result as a {@link Message} using an imperative assertion.
     * <p>
     * Throws an exception if the verifier fails.
     */
    @SuppressWarnings("unchecked")
    default <M extends Message> Then<R> verifyResultMessage(ThrowingConsumer<M> verifier) {
        return expectResultMessage(r -> {
            try {
                verifier.accept((M) r);
                return true;
            } catch (Throwable e) {
                run(() -> {
                    throw e;
                });
                return false;
            }
        }, "Custom verifier");
    }

    /**
     * Asserts that the result is not {@code null}.
     */
    default Then<R> expectNonNullResult() {
        return expectResult(Objects::nonNull);
    }

    /**
     * Asserts that no result was produced (i.e. {@code null}).
     */
    default Then<R> expectNoResult() {
        return expectResult((Object) null);
    }

    /**
     * Asserts that the result is not equal to the given value.
     * <p>
     * Useful for exclusion-based testing or negative assertions.
     */
    Then<R> expectNoResultLike(Object result);

    /**
     * Asserts that the result is a {@link Collection} or {@link Map} and contains values matching the given inputs.
     */
    @SuppressWarnings("unchecked")
    <T> Then<R> expectResultContaining(T... results);

    /**
     * Transforms the result using the provided {@code resultMapper} function for continued assertions.
     */
    <MR> Then<MR> mapResult(ThrowingFunction<? super R, ? extends MR> resultMapper);

    /**
     * Transforms the result message using the provided {@code resultMapper} function for continued assertions.
     */
    default <MR> Then<MR> mapResultMessage(ThrowingFunction<Message, ? extends MR> resultMapper) {
        return mapResult(r -> resultMapper.apply(castOrFail(r, Message.class)));
    }

    /**
     * Transforms the web response result using the provided {@code resultMapper} function for continued assertions.
     */
    default <MR> Then<MR> mapWebResultMessage(ThrowingFunction<WebResponse, ? extends MR> resultMapper) {
        return mapResult(r -> resultMapper.apply(castOrFail(r, WebResponse.class)));
    }

    /**
     * Returns the result produced during the {@code when}-phase of the test fixture, cast to type {@code T}.
     * <p>
     * If the result implements {@link HasMessage}, the payload of the underlying message is returned instead.
     * <p>
     * This provides access to the actual return value of the command, query, or other operation executed during
     * the test scenario.
     *
     * @param <T> the expected result type
     * @return the result of the {@code when}-phase, cast to {@code T}
     */
    <T> T getResult();

    /**
     * Returns the result produced during the {@code when}-phase of the test fixture, cast to the specified type.
     * <p>
     * If the result implements {@link HasMessage}, the payload of the underlying message is returned instead.
     * <p>
     * This provides access to the actual return value of the command, query, or other operation executed during
     * the test scenario.
     *
     * @param resultClass the expected result type
     * @param <T> the type parameter
     * @return the result of the {@code when}-phase, cast to {@code resultClass}
     */
    <T> T getResult(Class<T> resultClass);

    /**
     * Assigns the result of the {@code when}-phase to a named web parameter for use in subsequent requests.
     * <p>
     * This enables referencing the result (e.g. an ID returned from a POST operation) in later calls using path or
     * query parameter placeholders such as {@code {orderId}}.
     * <p>
     * If no explicit name is assigned using this method, Flux will implicitly bind the result to a single unnamed
     * parameter if only one is needed. For example:
     * <pre>
     * fixture.whenPost("/orders", "order-details.json")
     *        .andThen()
     *        .whenGet("/orders/{orderId}")
     *        .expectResult(Order.class);
     * </pre>
     * will work even without calling {@code asWebParameter("orderId")} because only one placeholder is present.
     * <p>
     * However, when multiple placeholders are used in a subsequent request (e.g. {@code {userId}} and
     * {@code {orderId}}), explicit calls to {@code asWebParameter(...)} are required to resolve which result maps to
     * which parameter:
     * <pre>
     * fixture.whenPost("/users", "user-details.json")
     *        .asWebParameter("userId")
     *        .andThen()
     *        .whenPost("/orders", "order-details.json")
     *        .asWebParameter("orderId")
     *        .andThen()
     *        .whenGet("/orders/{userId}/{orderId}")
     *        .expectResult(Order.class);
     * </pre>
     *
     * @param name the name of the web parameter to bind to the result
     * @return this {@code Then} instance for fluent chaining
     */
    Then<R> asWebParameter(String name);

    private <T> T castOrFail(Object t, Class<T> type) {
        try {
            return type.cast(t);
        } catch (ClassCastException e) {
            throw new GivenWhenThenAssertionError(format("Expected result of type %s, but was %s", type.getSimpleName(),
                                                         t == null ? "null" : t.getClass().getSimpleName()));
        }
    }

    /*
        Exceptions
     */

    /**
     * Asserts that the behavior under test completed exceptionally, and that the thrown exception matches the given
     * value.
     * <p>
     * Supported formats for the expected exception:
     * <ul>
     *   <li>A {@link Class} — checks if the exception is an instance of the class</li>
     *   <li>A {@link Predicate}, Hamcrest matcher — applied to the exception</li>
     *   <li>A {@code .json} path — deserialized and matched using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     *   <li>Any object — compared via {@link Objects#equals(Object, Object)}</li>
     * </ul>
     */
    Then<R> expectExceptionalResult(Object expectedException);

    /**
     * Asserts that the behavior under test completed exceptionally (with any non-null exception).
     */
    default Then<R> expectExceptionalResult() {
        return expectExceptionalResult(Objects::nonNull);
    }

    /**
     * Asserts that the behavior under test threw an exception that is an instance of the specified class.
     *
     * @param exceptionClass the expected exception type
     */
    default Then<R> expectExceptionalResult(@NonNull Class<? extends Throwable> exceptionClass) {
        return expectExceptionalResult(exceptionClass::isInstance,
                                       format("an instance of %s", exceptionClass.getSimpleName()));
    }

    /**
     * Asserts that the thrown exception matches the given {@link Predicate}.
     *
     * @param predicate condition to apply to the thrown exception
     */
    default <T extends Throwable> Then<R> expectExceptionalResult(ThrowingPredicate<T> predicate) {
        return expectExceptionalResult(predicate, "Predicate matcher");
    }

    /**
     * Asserts that the thrown exception matches the given {@link Predicate} and includes a description for error
     * messages.
     *
     * @param predicate    a matcher to apply
     * @param errorMessage a description used in case of failure
     */
    <T extends Throwable> Then<R> expectExceptionalResult(ThrowingPredicate<T> predicate, String errorMessage);

    /**
     * Verifies the thrown exception using the given {@link ThrowingConsumer}. The test fails if the consumer throws.
     *
     * @param verifier assertion logic to apply to the exception
     */
    @SuppressWarnings("unchecked")
    default <T extends Throwable> Then<R> verifyExceptionalResult(ThrowingConsumer<T> verifier) {
        return expectExceptionalResult(r -> {
            try {
                verifier.accept((T) r);
                return true;
            } catch (Throwable e) {
                run(() -> {
                    throw e;
                });
                return false;
            }
        }, "Custom verifier");
    }

    /**
     * Asserts that the behavior under test completed successfully (i.e. no exception thrown).
     */
    default Then<R> expectSuccessfulResult() {
        return expectResult(r -> !(r instanceof Throwable));
    }

    /**
     * Shortcut for asserting a successful result and casting it to a specific type.
     *
     * @param <R2> the expected result subtype
     */
    @SuppressWarnings("unchecked")
    default <R2 extends R> Then<R2> expectResult() {
        return (Then<R2>) expectSuccessfulResult();
    }

    /*
        Errors
     */

    /**
     * Asserts that an error occurred anywhere in a handler during the {@code when} phase.
     * <p>
     * This is distinct from {@link #expectExceptionalResult(Object)}: it catches thrown handler errors that did not
     * become the actual result (e.g. logged or swallowed exceptions).
     * <p>
     * You may pass:
     * <ul>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class}</li>
     *   <li>A {@code .json} resource string</li>
     *   <li>Any object for {@link Objects#equals} comparison</li>
     * </ul>
     */
    Then<R> expectError(Object expectedError);

    /**
     * Asserts that a handler error occurred matching the given predicate.
     *
     * @param predicate matcher to apply to the error
     */
    default <T extends Throwable> Then<R> expectError(ThrowingPredicate<T> predicate) {
        return expectError(predicate, "Predicate matcher");
    }

    /**
     * Verifies the handler error using a {@link ThrowingConsumer}.
     * <p>
     * The test fails if the verifier throws an exception.
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    default <T extends Throwable> Then<R> verifyError(ThrowingConsumer<T> verifier) {
        return this.expectError(e -> {
            try {
                verifier.accept((T) e);
                return true;
            } catch (Throwable ex) {
                run(() -> {
                    throw ex;
                });
                return false;
            }
        }, "Custom matcher");
    }

    /**
     * Asserts that any handler error occurred during the {@code when} phase.
     */
    default Then<R> expectError() {
        return expectError(Objects::nonNull);
    }

    /**
     * Asserts that an error occurred and it is an instance of the given class.
     */
    default Then<R> expectError(@NonNull Class<? extends Throwable> errorClass) {
        return expectError(errorClass::isInstance, format("an instance of %s", errorClass.getSimpleName()));
    }

    /**
     * Asserts that a handler error occurred matching the given predicate and includes a message on failure.
     *
     * @param predicate    matcher for the error
     * @param errorMessage description for assertion failure
     */
    <T extends Throwable> Then<R> expectError(ThrowingPredicate<T> predicate, String errorMessage);

    /**
     * Asserts that no errors were raised by any handler during the {@code when} phase.
     */
    Then<R> expectNoErrors();

    /*
        Metrics
     */

    /**
     * Asserts that one or more metrics were published during the {@code when} phase.
     * <p>
     * Each metric can be:
     * <ul>
     *   <li>A {@link Message} — matched including metadata</li>
     *   <li>A POJO — matched by payload only</li>
     *   <li>A {@link Predicate}, Hamcrest matcher, or {@link Class}</li>
     *   <li>A {@code .json} path — deserialized using {@link io.fluxcapacitor.common.serialization.JsonUtils}</li>
     * </ul>
     */
    Then<R> expectMetrics(Object... metrics);

    /**
     * Shorthand for asserting that at least one published metric matches the given predicate.
     *
     * @param predicate matcher applied to metric payloads
     */
    default <T> Then<R> expectMetric(ThrowingPredicate<T> predicate) {
        return expectMetrics(predicate.asPredicate());
    }

    /**
     * Asserts that only the specified metrics were published during the {@code when} phase.
     * <p>
     * Equivalent to {@code expectMetrics(...)} combined with an assertion that no other metrics occurred.
     */
    Then<R> expectOnlyMetrics(Object... metrics);

    /**
     * Asserts that none of the specified metrics were published.
     *
     * @param metrics the disallowed metrics
     */
    Then<R> expectNoMetricsLike(Object... metrics);

    /**
     * Asserts that no metrics were published.
     * <p>
     * Equivalent to {@code expectOnlyMetrics()}.
     */
    default Then<R> expectNoMetrics() {
        return expectOnlyMetrics();
    }

    /*
        Other
     */

    /**
     * Asserts an arbitrary condition against the current state of the {@link FluxCapacitor} instance.
     * <p>
     * Useful for validating system state, mock interactions, or side effects not covered by message assertions.
     *
     * @param check a consumer that performs assertions using the FluxCapacitor instance
     */
    Then<R> expectThat(Consumer<FluxCapacitor> check);

    /**
     * Asserts that the provided {@link Predicate} evaluates to {@code true} when applied to the current FluxCapacitor
     * instance.
     *
     * @param check predicate to evaluate
     */
    Then<R> expectTrue(ThrowingPredicate<FluxCapacitor> check);

    /**
     * Asserts that the provided {@link Predicate} evaluates to {@code false} when applied to the current FluxCapacitor
     * instance.
     * <p>
     * Equivalent to {@code expectTrue(check.negate())}.
     *
     * @param check predicate to evaluate
     */
    default Then<R> expectFalse(ThrowingPredicate<FluxCapacitor> check) {
        return expectTrue(check.negate());
    }

    /*
        And then
     */

    /**
     * Begins a new {@code given} phase from the current state, allowing chained scenario construction.
     * <p>
     * This is useful for multi-step flows: e.g. {@code given(...).when(...).then(...).andThen().when(...).then(...)}.
     */
    Given andThen();

}
