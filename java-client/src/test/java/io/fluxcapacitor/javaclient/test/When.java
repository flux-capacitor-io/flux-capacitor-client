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
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.common.serialization.RegisterType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.HttpRequestMethod;
import io.fluxcapacitor.javaclient.web.WebRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Defines the {@code when} phase of a behavioral Given-When-Then test using a {@link TestFixture}.
 * <p>
 * Use this interface to specify the action that triggers the behavior you want to test. Only the effects of the
 * {@code when} phase are considered in the {@code then} phase; effects from the {@code given} phase are ignored.
 * <p>
 * In all {@code whenXyz(...)} methods, any argument that is a {@link String} and ends with {@code .json} is interpreted
 * as the location of a JSON resource (e.g., {@code "/user/create-user.json"}). The resource will be loaded and
 * deserialized using {@link io.fluxcapacitor.common.serialization.JsonUtils}.
 * <p>
 * The JSON file must include a {@code @class} property to indicate the fully qualified class name of the object to
 * deserialize:
 * <pre>{@code
 * {
 *   "@class": "com.example.CreateUser",
 *   ...
 * }
 * }</pre>
 * <p>
 * It is also possible to refer to a class using its simple name or partial package path if the class or one of its
 * ancestor packages is annotated with {@link RegisterType @RegisterType}. For
 * example:
 * <pre>{@code
 * {
 *   "@class": "CreateUser"
 * }
 * }</pre>
 * or
 * <pre>{@code
 * {
 *   "@class": "example.CreateUser"
 * }
 * }</pre>
 * <p>
 * JSON files can extend other JSON files using {@code @extends}. The extension is recursive and merged deeply.
 *
 * @see When
 * @see TestFixture
 * @see JsonUtils
 * @see io.fluxcapacitor.common.serialization.RegisterType
 */
public interface When {

    /**
     * Executes the specified command and returns a result expectation.
     * <p>
     * If the command is a {@link Message}, it is dispatched as-is. Otherwise, it is wrapped using default metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCommand(Request<R> command) {
        return (Then<R>) whenCommand((Object) command);
    }

    /**
     * Executes the specified command and returns a result expectation.
     * <p>
     * If the command is a {@link Message}, it is dispatched as-is. Otherwise, it is wrapped using default metadata.
     */
    Then<Object> whenCommand(Object command);

    /**
     * Executes the specified command on behalf of a user and returns a result expectation.
     * <p>
     * The {@code user} may be a {@link User} instance or an ID resolved via {@link UserProvider}.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCommandByUser(Object user, Request<R> command) {
        return (Then<R>) whenCommandByUser(user, (Object) command);
    }

    /**
     * Executes the specified command on behalf of a user and returns a result expectation.
     * <p>
     * The {@code user} may be a {@link User} instance or an ID resolved via {@link UserProvider}.
     */
    Then<Object> whenCommandByUser(Object user, Object command);

    /**
     * Executes the given query and returns its expected result and/or side effects.
     * <p>
     * Query objects may be {@link Message} or plain objects.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenQuery(Request<R> query) {
        return (Then<R>) whenQuery((Object) query);
    }

    /**
     * Executes the given query and returns its expected result and/or side effects.
     * <p>
     * Query objects may be {@link Message} or plain objects.
     */
    Then<Object> whenQuery(Object query);

    /**
     * Executes the given query on behalf of a user and returns its result.
     * <p>
     * The {@code user} may be a {@link User} or an identifier resolved via {@link UserProvider}.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenQueryByUser(Object user, Request<R> query) {
        return (Then<R>) whenQueryByUser(user, (Object) query);
    }

    /**
     * Executes the given query on behalf of a user and returns its result.
     * <p>
     * The {@code user} may be a {@link User} or an identifier resolved via {@link UserProvider}.
     */
    Then<Object> whenQueryByUser(Object user, Object query);

    /**
     * Sends a message to the given custom topic and returns expectations for the result or side effects.
     */
    Then<Object> whenCustom(String topic, Object message);

    /**
     * Sends a request to the given custom topic and returns its expected result.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCustom(String topic, Request<R> request) {
        return (Then<R>) whenCustom(topic, (Object) request);
    }

    /**
     * Sends a request to a custom topic on behalf of a user and returns its result.
     * <p>
     * The {@code user} may be a {@link User} or ID resolved via {@link UserProvider}.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCustomByUser(Object user, String topic, Request<R> request) {
        return (Then<R>) whenCustomByUser(user, topic, (Object) request);
    }

    /**
     * Sends a message to a custom topic on behalf of a user and returns expectations for result or side effects.
     */
    Then<Object> whenCustomByUser(Object user, String topic, Object message);

    /**
     * Publishes the given event and returns expectations for its side effects.
     * <p>
     * Event objects may be {@link Message} or plain objects.
     */
    Then<?> whenEvent(Object event);

    /**
     * Applies events to a specific aggregate instance and publishes them.
     * <p>
     * Events may be {@link Message}, serialized {@link Data}, or POJOs.
     * Data will be upcasted and deserialized before applying.
     */
    default Then<?> whenEventsAreApplied(Id<?> aggregateId, Object... events) {
        return whenEventsAreApplied(aggregateId.toString(), aggregateId.getType(), events);
    }

    /**
     * Applies events to a specific aggregate instance and publishes them.
     * <p>
     * Events may be {@link Message}, serialized {@link Data}, or POJOs.
     * Data will be upcasted and deserialized before applying.
     */
    Then<?> whenEventsAreApplied(String aggregateId, Class<?> aggregateClass, Object... events);

    /**
     * Executes a search query on the specified collection and returns the expected result.
     *
     * @param collection   the collection to search in
     * @param searchQuery  the search query operator
     * @param <R>          the result type
     */
    <R> Then<List<R>> whenSearching(Object collection, UnaryOperator<Search> searchQuery);

    /**
     * Executes a search query with constraints on the specified collection and returns the expected result.
     *
     * @param collection  the collection to search in
     * @param constraints one or more constraints to apply
     * @param <R>         the result type
     */
    default <R> Then<List<R>> whenSearching(Object collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    /**
     * Executes a search query on the collection inferred from the class and returns the expected result.
     *
     * @param collection   the collection class
     * @param searchQuery  the search query operator
     * @param <R>          the result type
     */
    default <R> Then<List<R>> whenSearching(Class<R> collection, UnaryOperator<Search> searchQuery) {
        return this.whenSearching((Object) collection, searchQuery);
    }

    /**
     * Executes a search query with constraints on the collection inferred from the class and returns the expected result.
     *
     * @param collection   the collection class
     * @param constraints  the search constraints
     * @param <R>          the result type
     */
    default <R> Then<List<R>> whenSearching(Class<R> collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    /**
     * Executes the specified {@link WebRequest} and returns expectations for side effects or response.
     */
    Then<Object> whenWebRequest(WebRequest request);

    /**
     * Simulates a POST request to the specified path with the given payload.
     */
    default Then<Object> whenPost(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).url(path).payload(payload).build());
    }

    /**
     * Simulates a POST request to the specified path without a payload.
     */
    default Then<Object> whenPost(String path) {
        return whenPost(path, null);
    }

    /**
     * Simulates a PUT request to the specified path with the given payload.
     */
    default Then<Object> whenPut(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.PUT).url(path).payload(payload).build());
    }

    /**
     * Simulates a PATCH request to the specified path with the given payload.
     */
    default Then<Object> whenPatch(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.PATCH).url(path).payload(payload).build());
    }

    /**
     * Simulates a GET request to the specified path.
     */
    default Then<Object> whenGet(String path) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.GET).url(path).build());
    }

    /**
     * Simulates the expiration of a schedule and returns expectations for the triggered behavior.
     */
    Then<?> whenScheduleExpires(Object schedule);

    /**
     * Simulates advancing the test clock to the specified timestamp.
     * <p>
     * Any schedule that expires as a result will be triggered.
     */
    Then<?> whenTimeAdvancesTo(Instant timestamp);

    /**
     * Simulates advancing the test clock by the specified duration.
     * <p>
     * Any schedule that expires as a result will be triggered.
     */
    Then<?> whenTimeElapses(Duration duration);

    /**
     * Tests the behavior of upcasting the given value.
     * <p>
     * The value may be a {@link Data} instance or a {@link String} referencing a serialized Data resource.
     * If the value is a .json file reference, it will be loaded and deserialized accordingly.
     *
     * @param value the data to upcast
     * @param <R>   the resulting type
     * @return expectation for the upcast result
     */
    <R> Then<R> whenUpcasting(Object value);

    /**
     * Executes the provided action using the {@link FluxCapacitor} instance and validates its side effects.
     * <p>
     * Use this to simulate effects that bypass standard message types.
     *
     * @param action action to execute
     * @return expectation for the resulting behavior
     */
    default Then<?> whenExecuting(ThrowingConsumer<FluxCapacitor> action) {
        return whenApplying(fc -> {
            action.accept(fc);
            return null;
        });
    }

    /**
     * Executes the provided function using the {@link FluxCapacitor} instance and validates its result and/or side effects.
     *
     * @param action action to execute
     * @param <R>    result type
     * @return expectation for result and/or side effects
     */
    <R> Then<R> whenApplying(ThrowingFunction<FluxCapacitor, R> action);

    /**
     * Simulates a no-op phase to assert on the current state without invoking any behavior.
     * <p>
     * This is useful to assert on effects of the {@code given} phase alone or confirm that no further behavior occurs.
     *
     * @return expectation for the result (typically asserting nothing has changed)
     */
    default Then<?> whenNothingHappens() {
        return whenExecuting(fc -> {});
    }
}
