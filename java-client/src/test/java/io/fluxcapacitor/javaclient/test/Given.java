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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.ApplicationProperties;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.web.WebRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static io.fluxcapacitor.javaclient.common.ClientUtils.runSilently;

/**
 * Interface of the `given` phase of a behavioral given-when-then test. Here you specify everything that happened prior
 * to the action you want to test the behavior of. Any effects of the `given` phase will *not* be reported in the `then`
 * phase.
 * <p>
 * This interface extends from {@link When} meaning you can immediately skip ahead to the `when` phase if there was no
 * prior activity before your test.
 */
public interface Given extends When {

    /**
     * Specify one or more commands that have been issued prior to the behavior you want to test.
     * <p>
     * A command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command is
     * issued using the passed value as payload without additional metadata.
     */
    Given givenCommands(Object... commands);

    /**
     * Specify one or more commands that have been issued by given {@code user} prior to the behavior you want to test.
     * <p>
     * A command may be an instance of {@link Message} in which case it will be issued as is. Otherwise, the command is
     * issued using the passed value as payload without additional metadata.
     */
    Given givenCommandsByUser(User user, Object... commands);

    /**
     * Specify one or more events that have been applied to given aggregate prior to the behavior you want to test.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    default Given givenAppliedEvents(Id<?> aggregateId, Object... events) {
        return givenAppliedEvents(aggregateId.toString(), aggregateId.getType(), events);
    }

    /**
     * Specify one or more events that have been applied to given aggregate prior to the behavior you want to test.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be applied as is. An event may also be an
     * instance of serialized {@link Data}, which will automatically be upcasted and deserialized before applying.
     * Otherwise, the event is applied using the passed value as payload without additional metadata.
     */
    Given givenAppliedEvents(String aggregateId, Class<?> aggregateClass, Object... events);

    /**
     * Specify one or more events that have been published prior to the behavior you want to test.
     * <p>
     * An event may be an instance of {@link Message} in which case it will be published as is. Otherwise, the event is
     * published using the passed value as payload without additional metadata.
     */
    Given givenEvents(Object... events);

    /**
     * Specify a document that has been stored for search prior to the behavior you want to test.
     * <p>
     * The document will be stored in the given {@code collection} with random id and without start or end timestamp.
     */
    default Given givenDocument(Object document, Object collection) {
        return givenDocument(document, UUID.randomUUID().toString(), collection);
    }

    /**
     * Specify a document that has been stored for search prior to the behavior you want to test.
     * <p>
     * The document will be stored in the given {@code collection} with given {@code id} and without start or end
     * timestamp.
     */
    default Given givenDocument(Object document, Object id, Object collection) {
        return givenDocument(document, id, collection, null);
    }

    /**
     * Specify a document that has been stored for search prior to the behavior you want to test.
     * <p>
     * The document will be stored in the given {@code collection} with given {@code id} and given {@code timestamp} as
     * start and end timestamp.
     */
    default Given givenDocument(Object document, Object id, Object collection, Instant timestamp) {
        return givenDocument(document, id, collection, timestamp, timestamp);
    }

    /**
     * Specify a document that has been stored for search prior to the behavior you want to test.
     * <p>
     * The document will be stored in the given {@code collection} with given {@code id} and given start and end
     * timestamps.
     */
    Given givenDocument(Object document, Object id, Object collection, Instant start, Instant end);

    /**
     * Specify one or multiple documents that has been stored for search prior to the behavior you want to test.
     * <p>
     * The documents will be stored in the given {@code collection} with random id and without start or end timestamp.
     */
    Given givenDocuments(Object collection, Object... documents);

    /**
     * Specify one or more schedules that have been issued prior to the behavior you want to test.
     */
    default Given givenSchedules(Schedule... schedules) {
        return given(fc -> Arrays.stream(schedules).forEach(
                s -> runSilently(() ->fc.scheduler().schedule(s, false, Guarantee.STORED).get())));
    }

    /**
     * Specify one or more scheduled commands that have been issued prior to the behavior you want to test.
     */
    default Given givenScheduledCommands(Schedule... commands) {
        return given(fc -> Arrays.stream(commands).forEach(s -> fc.scheduler().scheduleCommand(s)));
    }

    /**
     * Specify one or more expired schedules that have been issued prior to the behavior you want to test.
     * <p>
     * A schedule may be an instance of {@link Message} in which case it will be published as is. Otherwise, the
     * schedule is issued using the passed value as payload without additional metadata.
     */
    default Given givenExpiredSchedules(Object... schedules) {
        return givenSchedules(
                Arrays.stream(schedules).map(p -> new Schedule(p, UUID.randomUUID().toString(), getCurrentTime()))
                        .toArray(Schedule[]::new));
    }

    /**
     * Simulates moving the time forward to given {@code timestamp} prior to testing for the expected behavior.
     * <p>
     * Any schedule that has expired by moving the time will be passed to handlers.
     */
    Given givenTimeAdvancedTo(Instant timestamp);

    /**
     * Simulates moving the time forward by given {@code duration} prior to testing for the expected behavior.
     * <p>
     * Any schedule that has expired by moving the time will be passed to handlers.
     */
    Given givenElapsedTime(Duration duration);

    /**
     * Specify a web request that has been issued prior to the behavior you want to test.
     */
    Given givenWebRequest(WebRequest webRequest);

    /**
     * Specify any action that has happened prior to the behavior you want to test.
     */
    Given given(ThrowingConsumer<FluxCapacitor> condition);

    /**
     * Returns the {@link FluxCapacitor} instance used by the test fixture.
     */
    FluxCapacitor getFluxCapacitor();

    /**
     * Get the clock used by this test fixture.
     */
    default Clock getClock() {
        return getFluxCapacitor().clock();
    }

    /**
     * Get the current time of this test fixture.
     */
    default Instant getCurrentTime() {
        return getClock().instant();
    }

    /**
     * Sets the clock used by this test fixture. By default, the test fixture fixes its clock when it is created, but if
     * the result of your test depends on the time at which it is run you can fix the test fixture's clock using this
     * method.
     */
    Given withClock(Clock clock);

    /**
     * Fixes the time of the test fixture. By default, the test fixture fixes its clock when it is created, but if the
     * result of your test depends on the time at which it is run you can fix the test fixture's time using this
     * method.
     */
    Given atFixedTime(Instant time);

    /**
     * Sets a property for the duration of the test fixture, assuming that components obtain their properties via
     * {@link ApplicationProperties}.
     */
    Given withProperty(String name, Object value);

}
