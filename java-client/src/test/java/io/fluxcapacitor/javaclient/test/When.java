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
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.web.WebRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.function.UnaryOperator;

/**
 * Interface of the `when` phase of a behavioral given-when-then test. Here you specify the action you want to test the
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
    Then whenCommand(Object command);

    /**
     * Test expected behavior of handling the given command issued by the given user, including any side effects.
     * <p>
     * The command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command
     * is issued using the passed value as payload without additional metadata.
     */
    Then whenCommandByUser(Object command, User user);

    /**
     * Test expected result of the given query (or side effects if any).
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    Then whenQuery(Object query);

    /**
     * Test expected result of the given query issued by the given user (or side effects if any).
     * <p>
     * The query may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the query is
     * issued using the passed value as payload without additional metadata.
     */
    Then whenQueryByUser(Object command, User user);

    /**
     * Test expected behavior of handling the given event, including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the event is
     * issued using the passed value as payload without additional metadata.
     */
    Then whenEvent(Object event);

    /**
     * Test expected behavior of applying the given events on the given aggregate and then publishing those events,
     * including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    default Then whenEventsAreApplied(Id<?> aggregateId, Object... events) {
        return whenEventsAreApplied(aggregateId.toString(), aggregateId.getType(), events);
    }

    /**
     * Test expected behavior of applying the given events on the given aggregate and then publishing those events,
     * including any side effects.
     * <p>
     * The event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    Then whenEventsAreApplied(String aggregateId, Class<?> aggregateClass, Object... events);

    /**
     * Test expected result of the given search in given collection.
     */
    Then whenSearching(String collection, UnaryOperator<Search> searchQuery);

    /**
     * Test expected result of a search with given constraints in given collection.
     */
    default Then whenSearching(String collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    /**
     * Test expected behavior of handling the given web request, including any side effects.
     */
    Then whenWebRequest(WebRequest request);

    /**
     * Test expected behavior of handling the given expired schedule.
     * <p>
     * The schedule may be an instance of {@link Message} if you need to include metadata. Otherwise, the schedule is
     * issued using the passed value as payload without additional metadata.
     */
    Then whenScheduleExpires(Object schedule);

    /**
     * Test expected behavior after simulating a time advance to the given timestamp.
     */
    Then whenTimeAdvancesTo(Instant timestamp);

    /**
     * Test expected behavior after simulating a time advance by the given duration.
     */
    Then whenTimeElapses(Duration duration);

    /**
     * Test expected (side) effect of the given action.
     */
    default Then whenExecuting(ThrowingConsumer<FluxCapacitor> action) {
        return whenApplying(fc -> {
            action.accept(fc);
            return null;
        });
    }

    /**
     * Test expected result and/or (side) effects of the given action.
     */
    Then whenApplying(ThrowingFunction<FluxCapacitor, ?> action);
}
