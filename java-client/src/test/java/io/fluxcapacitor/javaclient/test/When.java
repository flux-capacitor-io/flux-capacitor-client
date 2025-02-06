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
 * Interface of the `when` phase of a behavioral given-when-Then<?> test. Here you specify the action you want to test the
 * behavior of.
 * <p>
 * Only effects of the `when` phase will be reported in the `then` phase, i.e. effects of the `given` phase will *not*
 * be reported.
 */
public interface When {

    /**
     * Test expected behavior of handling the given command, including any side effects.
     * <p>
     * The command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command
     * is issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCommand(Request<R> command) {
        return (Then<R>) whenCommand((Object) command);
    }

    /**
     * Test expected behavior of handling the given command, including any side effects.
     * <p>
     * The command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command
     * is issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenCommand(Object command);

    /**
     * Test expected behavior of handling the given command issued by the given user, including any side effects.
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command
     * is issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCommandByUser(Object user, Request<R> command) {
        return (Then<R>) whenCommandByUser(user, (Object) command);
    }

    /**
     * Test expected behavior of handling the given command issued by the given user, including any side effects.
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command
     * is issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenCommandByUser(Object user, Object command);

    /**
     * Test expected result of the given query (or side effects if any).
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenQuery(Request<R> query) {
        return (Then<R>) whenQuery((Object) query);
    }

    /**
     * Test expected result of the given query (or side effects if any).
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenQuery(Object query);

    /**
     * Test expected result of the given query issued by the given user (or side effects if any).
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenQueryByUser(Object user, Request<R> query) {
        return (Then<R>) whenQueryByUser(user, (Object) query);
    }

    /**
     * Test expected result of the given query issued by the given user (or side effects if any).
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenQueryByUser(Object user, Object query);

    /**
     * Test expected behavior of handling the given message for given custom topic, including any side effects.
     * <p>
     * The message may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the
     * message issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenCustom(String topic, Object message);

    /**
     * Test expected behavior of handling the given request for given custom topic, including any side effects.
     * <p>
     * The request may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the request is
     * issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCustom(String topic, Request<R> request) {
        return (Then<R>) whenCustom(topic, (Object) request);
    }

    /**
     * Test expected result of the given request for given custom topic issued by the given user (or side effects if any).
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The request may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the request is
     * issued using the passed value as payload without additional metadata.
     */
    @SuppressWarnings("unchecked")
    default <R> Then<R> whenCustomByUser(Object user, String topic, Request<R> request) {
        return (Then<R>) whenCustomByUser(user, topic, (Object) request);
    }

    /**
     * Test expected result of the given message for given custom topic issued by the given user (or side effects if any).
     * <p>
     * The given {@code user} may be an instance of {@link User} or an object representing the user's id. In the latter
     * case, the test fixture will use the {@link UserProvider} to provide the user by id.
     * <p>
     * The message may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the message is
     * issued using the passed value as payload without additional metadata.
     */
    Then<Object> whenCustomByUser(Object user, String topic, Object message);

    /**
     * Test expected behavior of handling the given event, including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the event is
     * issued using the passed value as payload without additional metadata.
     */
    Then<?> whenEvent(Object event);

    /**
     * Test expected behavior of applying the given events on the given aggregate and Then<?> publishing those events,
     * including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    default Then<?> whenEventsAreApplied(Id<?> aggregateId, Object... events) {
        return whenEventsAreApplied(aggregateId.toString(), aggregateId.getType(), events);
    }

    /**
     * Test expected behavior of applying the given events on the given aggregate and Then<?> publishing those events,
     * including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    Then<?> whenEventsAreApplied(String aggregateId, Class<?> aggregateClass, Object... events);

    /**
     * Test expected result of the given search in given collection.
     */
    <R> Then<List<R>> whenSearching(Object collection, UnaryOperator<Search> searchQuery);

    /**
     * Test expected result of a search with given constraints in given collection.
     */
    default <R> Then<List<R>> whenSearching(Object collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    /**
     * Test expected result of the given search in given collection.
     */
    default <R> Then<List<R>> whenSearching(Class<R> collection, UnaryOperator<Search> searchQuery) {
        return this.whenSearching((Object) collection, searchQuery);
    }

    /**
     * Test expected result of a search with given constraints in given collection.
     */
    default <R> Then<List<R>> whenSearching(Class<R> collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    /**
     * Test expected behavior of handling the given web request, including any side effects.
     */
    Then<Object> whenWebRequest(WebRequest request);

    /**
     * Test expected behavior of handling the given POST request, including any side effects.
     */
    default Then<Object> whenPost(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).url(path).payload(payload).build());
    }

    /**
     * Test expected behavior of handling the given PUT request, including any side effects.
     */
    default Then<Object> whenPut(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.PUT).url(path).payload(payload).build());
    }

    /**
     * Test expected behavior of handling the given PUT request, including any side effects.
     */
    default Then<Object> whenPatch(String path, Object payload) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.PATCH).url(path).payload(payload).build());
    }

    /**
     * Test expected behavior of handling the given GET request, including any side effects.
     */
    default Then<Object> whenGet(String path) {
        return whenWebRequest(WebRequest.builder().method(HttpRequestMethod.GET).url(path).build());
    }

    /**
     * Test expected behavior of handling the given expired schedule.
     * <p>
     * The schedule may be an instance of {@link Message} if you need to include metadata. Otherwise, the schedule is
     * issued using the passed value as payload without additional metadata.
     */
    Then<?> whenScheduleExpires(Object schedule);

    /**
     * Test expected behavior after simulating a time advance to the given timestamp.
     */
    Then<?> whenTimeAdvancesTo(Instant timestamp);

    /**
     * Test expected behavior after simulating a time advance by the given duration.
     */
    Then<?> whenTimeElapses(Duration duration);

    /**
     * Test expected (side) effect of the given action.
     */
    default Then<?> whenExecuting(ThrowingConsumer<FluxCapacitor> action) {
        return whenApplying(fc -> {
            action.accept(fc);
            return null;
        });
    }

    /**
     * Test expected result and/or (side) effects of the given action.
     */
    <R> Then<R> whenApplying(ThrowingFunction<FluxCapacitor, R> action);

    /**
     * Test for state after the given phase.
     */
    default Then<?> whenNothingHappens() {
        return whenExecuting(fc -> {
        });
    }
}
