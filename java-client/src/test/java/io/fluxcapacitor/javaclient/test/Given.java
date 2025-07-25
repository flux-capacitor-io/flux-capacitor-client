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
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.ApplicationProperties;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.HttpRequestMethod;
import io.fluxcapacitor.javaclient.web.WebRequest;

import java.net.HttpCookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Defines the {@code given} phase of a behavioral Given-When-Then test using a {@link TestFixture}.
 * <p>
 * Use this interface to declare all prior context before executing the behavior you want to test. This can include
 * commands, events, schedules, documents, requests, and more. Any effects introduced during this phase
 * <strong>will not</strong> be included in the {@code then} phase assertions.
 * <p>
 * This interface extends {@link When}, allowing you to skip directly to the {@code when} phase if no prior activity is required.
 * <p>
 * In all {@code givenXyz(...)} methods, any argument that is a {@link String} and ends with {@code .json} is
 * interpreted as the location of a JSON resource (e.g., {@code "/user/create-user.json"}). The resource will be
 * loaded and deserialized using {@link JsonUtils}.
 * <p>
 * The JSON file must include a {@code @class} property to indicate the fully qualified class name of the object to deserialize:
 * <pre>{@code
 * {
 *   "@class": "com.example.CreateUser",
 *   ...
 * }
 * }</pre>
 * <p>
 * It is also possible to refer to a class using its simple name or partial package path if the class or one of its
 * ancestor packages is annotated with {@link io.fluxcapacitor.common.serialization.RegisterType @RegisterType}.
 * For example, if {@code @RegisterType} is present on the {@code com.example} package, you may use:
 * <pre>{@code
 * {
 *   "@class": "CreateUser"
 * }
 * }
 * or
 * {
 *   "@class": "example.CreateUser"
 * }
 * }</pre>
 * <p>
 * JSON files can also extend other JSON files via an {@code @extends} property. The contents of the referenced file
 * will be recursively merged, with the extending file's values overriding nested properties as needed:
 * <pre>{@code
 * {
 *   "@extends": "/user/create-user.json",
 *   "details" : {
 *       "lastName": "Johnson"
 *   }
 * }
 * }</pre>
 * This behavior is described in more detail in {@link JsonUtils}.
 *
 * @see When
 * @see TestFixture
 * @see JsonUtils
 * @see io.fluxcapacitor.common.serialization.RegisterType
 */
public interface Given extends When {

    /**
     * Specifies one or more commands that were issued before the behavior under test.
     * <p>
     * Each command can be a raw {@link Message} or a plain object. If the value is not a {@code Message}, it will be
     * wrapped with default metadata and used as the message payload.
     * <p>
     * You may also pass a {@code String} ending in {@code .json} to load a payload from a resource file.
     */
    Given givenCommands(Object... commands);

    /**
     * Specifies one or more commands that were issued by the specified {@code user} before the behavior under test.
     * <p>
     * The user may be a {@link User} object or an identifier. If an ID is provided, the {@link UserProvider} will
     * resolve it to a {@code User}.
     * <p>
     * Each command can be a raw {@link Message} or a plain object. If not a {@code Message}, it will be wrapped as a
     * payload.
     */
    Given givenCommandsByUser(Object user, Object... commands);

    /**
     * Specifies one or more requests that were sent to the given custom message {@code topic} before the behavior under
     * test.
     * <p>
     * Each request may be a {@link Message} or a plain object to be wrapped with default metadata.
     */
    Given givenCustom(String topic, Object... requests);

    /**
     * Specifies one or more requests that were sent to the given custom {@code topic} by the specified {@code user}.
     * <p>
     * The user may be a {@link User} object or an identifier, resolved via {@link UserProvider} if needed.
     * <p>
     * Each request may be a {@link Message} or a plain object to be wrapped with default metadata.
     */
    Given givenCustomByUser(Object user, String topic, Object... requests);

    /**
     * Simulates one or more events that were previously applied to a specific aggregate.
     * <p>
     * Events can be {@link Message}, {@link Data} (auto-deserialized and upcasted), or plain objects.
     * <p>
     * You may also pass a {@code String} ending in {@code .json} to load an event from a resource file.
     */
    default Given givenAppliedEvents(Id<?> aggregateId, Object... events) {
        return givenAppliedEvents(aggregateId.toString(), aggregateId.getType(), events);
    }

    /**
     * Simulates one or more events that were previously applied to a specific aggregate.
     * <p>
     * Events can be {@link Message}, {@link Data} (auto-deserialized and upcasted), or plain objects.
     */
    Given givenAppliedEvents(String aggregateId, Class<?> aggregateClass, Object... events);

    /**
     * Publishes one or more events that were emitted before the behavior under test.
     * <p>
     * Events may be {@link Message} or plain objects.
     */
    Given givenEvents(Object... events);

    /**
     * Stores a document in the search index before the behavior under test.
     * <p>
     * If the object is annotated with {@link Searchable}, the annotation metadata will determine how it's indexed.
     */
    Given givenDocument(Object document);

    /**
     * Stores a document in the given {@code collection} with a generated ID and no timestamps.
     */
    default Given givenDocument(Object document, Object collection) {
        return givenDocument(document, getFluxCapacitor().identityProvider().nextTechnicalId(), collection);
    }

    /**
     * Stores a document in the given {@code collection} with a specific {@code id} and no timestamps.
     */
    default Given givenDocument(Object document, Object id, Object collection) {
        return givenDocument(document, id, collection, null);
    }

    /**
     * Stores a document with a specific {@code id} and timestamp in the given {@code collection}. The timestamp is used
     * as both start and end time.
     */
    default Given givenDocument(Object document, Object id, Object collection, Instant timestamp) {
        return givenDocument(document, id, collection, timestamp, timestamp);
    }

    /**
     * Stores a document with specific {@code id}, {@code collection}, and time range.
     */
    Given givenDocument(Object document, Object id, Object collection, Instant start, Instant end);

    /**
     * Stores multiple documents in the given {@code collection} with random IDs and no timestamps.
     */
    Given givenDocuments(Object collection, Object firstDocument, Object... otherDocuments);

    /**
     * Registers a stateful handler instance before the behavior under test.
     * <p>
     * Internally delegates to {@link #givenDocument(Object)}.
     *
     * @see io.fluxcapacitor.javaclient.tracking.handling.Stateful
     */
    default Given givenStateful(Object stateful) {
        return givenDocument(stateful);
    }

    /**
     * Schedules one or more commands before the behavior under test.
     */
    Given givenSchedules(Schedule... schedules);

    /**
     * Issues one or more scheduled commands before the behavior under test.
     */
    Given givenScheduledCommands(Schedule... commands);

    /**
     * Simulates expired schedules that should be processed before the behavior under test.
     * <p>
     * If the object is not a {@link Message}, it is wrapped as a payload with a generated ID and the current time.
     */
    default Given givenExpiredSchedules(Object... schedules) {
        return givenSchedules(
                Arrays.stream(schedules).map(p -> new Schedule(
                                p, getFluxCapacitor().identityProvider().nextTechnicalId(), getCurrentTime()))
                        .toArray(Schedule[]::new));
    }

    /**
     * Advances the test clock to the specified {@code timestamp} before the behavior under test.
     * <p>
     * Any schedules that expire during the jump will be processed.
     */
    Given givenTimeAdvancedTo(Instant timestamp);

    /**
     * Advances the test clock by the given {@code duration} before the behavior under test.
     * <p>
     * Any schedules that expire during the jump will be processed.
     */
    Given givenElapsedTime(Duration duration);

    /**
     * Simulates a web request that was issued before the behavior under test.
     */
    Given givenWebRequest(WebRequest webRequest);

    /**
     * Simulates a POST request to the specified {@code path} with the given {@code payload}.
     */
    default Given givenPost(String path, Object payload) {
        return givenWebRequest(
                WebRequest.builder().method(HttpRequestMethod.POST).url(path).payload(payload).build());
    }

    /**
     * Simulates a PUT request to the specified {@code path} with the given {@code payload}.
     */
    default Given givenPut(String path, Object payload) {
        return givenWebRequest(
                WebRequest.builder().method(HttpRequestMethod.PUT).url(path).payload(payload).build());
    }

    /**
     * Simulates a PATCH request to the specified {@code path} with the given {@code payload}.
     */
    default Given givenPatch(String path, Object payload) {
        return givenWebRequest(
                WebRequest.builder().method(HttpRequestMethod.PATCH).url(path).payload(payload).build());
    }

    /**
     * Simulates a DELETE request to the specified {@code path}.
     */
    default Given givenDelete(String path) {
        return givenWebRequest(
                WebRequest.builder().method(HttpRequestMethod.DELETE).url(path).build());
    }

    /**
     * Simulates a GET request to the specified {@code path}.
     */
    default Given givenGet(String path) {
        return givenWebRequest(WebRequest.builder().method(HttpRequestMethod.GET).url(path).build());
    }

    /**
     * Performs any arbitrary precondition using the {@link FluxCapacitor} instance directly.
     */
    Given given(ThrowingConsumer<FluxCapacitor> condition);

    /**
     * Returns the {@link FluxCapacitor} instance used in this test fixture.
     */
    FluxCapacitor getFluxCapacitor();

    /**
     * Returns the clock used by this test fixture.
     */
    default Clock getClock() {
        return getFluxCapacitor().clock();
    }

    /**
     * Returns the current time according to the test fixture's clock.
     */
    default Instant getCurrentTime() {
        return getClock().instant();
    }

    /**
     * Adds a cookie to be used in subsequent simulated web requests.
     */
    default Given withCookie(String name, String value) {
        return withCookie(new HttpCookie(name, value));
    }

    /**
     * Adds a cookie to be used in subsequent simulated web requests.
     * <p>
     * Expired cookies will remove any matching previously added cookie.
     */
    Given withCookie(HttpCookie cookie);

    /**
     * Adds or removes headers to be used in subsequent simulated web requests.
     * <p>
     * If {@code headerValues} is empty, the header will be removed.
     */
    Given withHeader(String headerName, String... headerValues);

    /**
     * Removes a previously set header for simulated web requests.
     */
    default Given withoutHeader(String headerName) {
        return withHeader(headerName);
    }

    /**
     * Overrides the test fixture's clock. Use this to simulate time-based behavior explicitly.
     */
    Given withClock(Clock clock);

    /**
     * Fixes the time at which the test fixture starts.
     */
    Given atFixedTime(Instant time);

    /**
     * Sets an application property for the duration of the test fixture.
     * <p>
     * Only effective if your components resolve properties through {@link ApplicationProperties}.
     */
    Given withProperty(String name, Object value);

    /**
     * Registers a singleton (injected) bean to be used during the test fixture.
     * <p>
     * The bean will be available for injection into handlers that use
     * {@link org.springframework.beans.factory.annotation.Autowired}.
     */
    Given withBean(Object bean);

    /**
     * Instructs the test fixture to ignore any exceptions that occur during the {@code Given} phase.
     * <p>
     * Errors in a later phase will still be recorded.
     */
    Given ignoringErrors();
}
