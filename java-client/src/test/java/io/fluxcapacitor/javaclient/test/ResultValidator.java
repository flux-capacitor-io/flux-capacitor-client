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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class ResultValidator<R> implements Then<R> {
    static final boolean matchersSupported = ReflectionUtils.classExists("org.hamcrest.Matcher");
    
    private final TestFixture testFixture;
    @Getter(AccessLevel.PROTECTED)
    private final FluxCapacitor fluxCapacitor;
    @With
    private Object result;
    private final List<Message> events, commands, queries, webRequests, webResponses, metrics;
    private final List<Schedule> newSchedules;
    private final List<Schedule> allSchedules;
    private final List<Throwable> errors;

    public ResultValidator(TestFixture testFixture) {
        this.testFixture = testFixture;
        fluxCapacitor = testFixture.getFluxCapacitor();
        var fixtureResult = testFixture.getFixtureResult();
        result = fixtureResult.getResult();
        events = fixtureResult.getEvents();
        commands = fixtureResult.getCommands();
        queries = fixtureResult.getQueries();
        webRequests = fixtureResult.getWebRequests();
        webResponses = fixtureResult.getWebResponses();
        metrics = fixtureResult.getMetrics();
        newSchedules = fixtureResult.getSchedules();
        allSchedules = testFixture.getFutureSchedules();
        errors = fixtureResult.getErrors();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <MR> Then<MR> mapResult(Function<? super R, ? extends MR> resultMapper) {
        return (Then<MR>) withResult(resultMapper.apply((R) result));
    }

    @Override
    public Then<R> expectEvents(Object... events) {
        return expect(asMessages(events), this.events);
    }

    @Override
    public Then<R> expectOnlyEvents(Object... events) {
        return expectOnly(asMessages(events), this.events);
    }

    @Override
    public Then<R> expectNoEventsLike(Object... events) {
        return expectNo(asMessages(events), this.events);
    }

    @Override
    public Then<R> expectCommands(Object... commands) {
        return expect(asMessages(commands), this.commands);
    }

    @Override
    public Then<R> expectOnlyCommands(Object... commands) {
        return expectOnly(asMessages(commands), this.commands);
    }

    @Override
    public Then<R> expectNoCommandsLike(Object... commands) {
        return expectNo(asMessages(commands), this.commands);
    }

    @Override
    public Then<R> expectQueries(Object... queries) {
        return expect(asMessages(queries), this.queries);
    }

    @Override
    public Then<R> expectOnlyQueries(Object... queries) {
        return expectOnly(asMessages(queries), this.queries);
    }

    @Override
    public Then<R> expectNoQueriesLike(Object... queries) {
        return expectNo(asMessages(queries), this.queries);
    }

    @Override
    public Then<R> expectWebRequests(Object... webRequests) {
        return expect(asMessages(webRequests), this.webRequests);
    }

    @Override
    public Then<R> expectOnlyWebRequests(Object... webRequests) {
        return expectOnly(asMessages(webRequests), this.webRequests);
    }

    @Override
    public Then<R> expectNoWebRequestsLike(Object... webRequests) {
        return expectNo(asMessages(this.webRequests), this.webRequests);
    }

    @Override
    public Then<R> expectWebResponses(Object... webResponses) {
        return expect(asMessages(webResponses), this.webResponses);
    }

    @Override
    public Then<R> expectOnlyWebResponses(Object... webResponses) {
        return expectOnly(asMessages(webResponses), this.webResponses);
    }

    @Override
    public Then<R> expectNoWebResponsesLike(Object... webResponses) {
        return expectNo(asMessages(webResponses), this.webResponses);
    }

    @Override
    public Then<R> expectOnlyNewSchedules(Object... schedules) {
        return expectOnlyScheduledMessages(asMessages(schedules), this.newSchedules);
    }

    @Override
    public Then<R> expectNewSchedules(Object... schedules) {
        return expectScheduledMessages(asMessages(schedules), this.newSchedules);
    }

    @Override
    public Then<R> expectNoNewSchedulesLike(Object... schedules) {
        return expectNo(asMessages(schedules), this.newSchedules);
    }

    @Override
    public Then<R> expectOnlySchedules(Object... schedules) {
        return expectOnlyScheduledMessages(asMessages(schedules), this.allSchedules);
    }

    @Override
    public Then<R> expectSchedules(Object... schedules) {
        return expectScheduledMessages(asMessages(schedules), this.allSchedules);
    }

    @Override
    public Then<R> expectNoSchedulesLike(Object... schedules) {
        return expectNo(asMessages(schedules), this.allSchedules);
    }

    @Override
    public ResultValidator<R> expectResult(Object expectedResult) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> {
            Object expected = testFixture.parseObject(expectedResult, callerClass);
            if (result instanceof Throwable e) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      expected, describeException(e), e);
            }
            if (!matches(expected, result)) {
                if (isComparableToActual(expected)) {
                    throw new GivenWhenThenAssertionError(
                            "Handler returned a result of unexpected type",
                            expected.getClass(), result.getClass());
                }
                throw new GivenWhenThenAssertionError(
                        "Handler returned an unexpected result", expected, result);
            }
            return this;
        });
    }

    @Override
    public <M extends Message> Then<R> expectResultMessage(Predicate<M> messagePredicate, String description) {
        if (result instanceof Throwable e) {
            throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                  description, describeException(e), e);
        }
        if (result instanceof Message) {
            if (!testSafely(messagePredicate, result)) {
                if (!errors.isEmpty()) {
                    throw new GivenWhenThenAssertionError(
                            "Handler returned an unexpected result. Probable cause is an exception during handling.",
                            description, describeException(errors.getFirst()), errors.getFirst());
                }
                throw new GivenWhenThenAssertionError(
                        "Handler returned an unexpected result", description, result);
            }
            return this;
        }
        throw new GivenWhenThenAssertionError(
                "Test fixture result is not of type Message.", description, result);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R2 extends R> Then<R2> expectResult(Predicate<R2> predicate, String description) {
        return fluxCapacitor.apply(fc -> {
            if (result instanceof Throwable e) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling", e);
            }
            if (!matches(predicate, result)) {
                if (!errors.isEmpty()) {
                    throw new GivenWhenThenAssertionError("Handler returned an unexpected result. "
                                                          + "Probable cause is an exception during handling.",
                                                          description, describeException(errors.getFirst()),
                                                          errors.getFirst());
                }
                throw new GivenWhenThenAssertionError("Handler returned an unexpected result",
                                                      description, result);
            }
            return (Then<R2>) this;
        });
    }

    @Override
    public ResultValidator<R> expectNoResultLike(Object value) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> {
            Object notExpected = testFixture.parseObject(value, callerClass);
            if (result instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) result);
            }
            if (matches(notExpected, result)) {
                throw new GivenWhenThenAssertionError(
                        format("Handler returned the unwanted result.\nExpected not to get: %s\nGot: %s",
                               notExpected, result));
            }
            return this;
        });
    }

    @SafeVarargs
    @Override
    public final <T> Then<R> expectResultContaining(T... results) {
        if (!(this.result instanceof Collection<?>)) {
            throw new GivenWhenThenAssertionError("Result is not a collection", List.of(results), this.result);
        }
        return expect(List.of(results), (Collection<?>) this.result);
    }

    @Override
    public ResultValidator<R> expectExceptionalResult(@NonNull Object expectedException) {
        return fluxCapacitor.apply(fc -> {
            if (!(result instanceof Throwable)) {
                throw new GivenWhenThenAssertionError(
                        "Handler returned normally but an exception was expected",
                        expectedException, result);
            }
            if (!matches(expectedException, result)) {
                if (isComparableToActual(expectedException)) {
                    throw new GivenWhenThenAssertionError("Handler threw unexpected exception",
                            expectedException.getClass(), result.getClass());
                }
                throw new GivenWhenThenAssertionError("Handler threw unexpected exception",
                                                      expectedException, result);
            }
            return this;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Throwable> Then<R> expectExceptionalResult(Predicate<T> predicate, String description) {
        return fluxCapacitor.apply(fc -> {
            if (!(result instanceof Throwable)) {
                throw new GivenWhenThenAssertionError(
                        "Handler returned normally but an exception was expected",
                        description, result);
            }
            if (!predicate.test((T) result)) {
                throw new GivenWhenThenAssertionError("Handler threw unexpected exception",
                                                      description, result);
            }
            return this;
        });
    }

    @Override
    public Then<R> expectError(Object expectedError) {
        if (errors.isEmpty()) {
            throw new GivenWhenThenAssertionError("An error was expected but none was published",
                                                  expectedError, null);
        }
        return expect(List.of(expectedError), this.errors);
    }

    @Override
    public <T extends Throwable> Then<R> expectError(Predicate<T> predicate, String description) {
        if (errors.isEmpty()) {
            throw new GivenWhenThenAssertionError("An error was expected but none was published",
                                                  description, null);
        }
        try {
            expect(List.of(predicate), this.errors);
        } catch (GivenWhenThenAssertionError e) {
            throw new GivenWhenThenAssertionError("An unexpected error was published",
                                                  description, result);
        }
        return this;
    }

    @Override
    public Then<R> expectNoErrors() {
        if (!errors.isEmpty()) {
            throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                  errors.getFirst());
        }
        return this;
    }
    @Override
    public Then<R> expectMetrics(Object... metrics) {
        return expect(asMessages(metrics), this.metrics);
    }

    @Override
    public Then<R> expectOnlyMetrics(Object... metrics) {
        return expectOnly(asMessages(metrics), this.metrics);
    }

    @Override
    public Then<R> expectNoMetricsLike(Object... metrics) {
        return expectNo(asMessages(metrics), this.metrics);
    }

    @Override
    public ResultValidator<R> expectThat(Consumer<FluxCapacitor> check) {
        return fluxCapacitor.apply(fc -> {
            try {
                check.accept(fc);
            } catch (Throwable e) {
                if (!errors.isEmpty()) {
                    throw new GivenWhenThenAssertionError(String.format(
                            "Verify check failed: %s\nProbable cause is an exception during handling.", e.getMessage()),
                                                          (errors.getFirst()));
                }
                throw new GivenWhenThenAssertionError("Verify check failed", e);
            }
            return this;
        });
    }

    @Override
    public Then<R> expectTrue(Predicate<FluxCapacitor> check) {
        return fluxCapacitor.apply(fc -> {
            if (!check.test(fc)) {
                throw new GivenWhenThenAssertionError("Predicate test failed");
            }
            return this;
        });
    }

    @Override
    public TestFixture andThen() {
        return testFixture.reset();
    }

    protected boolean isComparableToActual(Object expected) {
        return result != null && expected != null && !isMatcher(expected)
               && !(expected instanceof Collection<?> && result instanceof Collection<?>)
               && !(expected instanceof Map<?, ?> && result instanceof Map<?, ?>)
               && !Objects.equals(expected.getClass(), result.getClass());
    }

    protected ResultValidator<R> expectScheduledMessages(Collection<?> expected, Collection<? extends Schedule> actual) {
        return fluxCapacitor.apply(fc -> {
            if (!expected.isEmpty() && actual.isEmpty()) {
                throw new GivenWhenThenAssertionError("No messages were scheduled");
            }
            expected.forEach(e -> {
                if (e instanceof Schedule) {
                    if (actual.stream().noneMatch(s -> Objects.equals(s.getDeadline(), ((Schedule) e).getDeadline()))) {
                        throw new GivenWhenThenAssertionError(
                                "Found no schedules with matching deadline",
                                       ((Schedule) e).getDeadline(),
                                       actual.stream().map(Schedule::getDeadline).collect(toList()));
                    }
                }
            });
            return expect(asMessages(expected), actual);
        });
    }


    protected ResultValidator<R> expect(Collection<?> expected, Collection<?> actual) {
        return fluxCapacitor.apply(fc -> {
            if (!containsAll(expected, actual)) {
                List<?> remaining = new ArrayList<>(actual);
                List<?> filtered = expected.stream().flatMap(e -> {
                    if (e != null && !isMatcher(expected) && !(expected instanceof Predicate<?>)) {
                        Class<?> payloadType =
                                e instanceof Message ? ((Message) e).getPayload().getClass() : expected.getClass();
                        Object match = remaining.stream().filter(a -> payloadType
                                        .equals(a instanceof Message ? ((Message) a).getPayload().getClass() : a.getClass()))
                                .findFirst().orElse(null);
                        if (match != null) {
                            remaining.remove(match);
                            return Stream.of(match);
                        }
                    }
                    return Stream.empty();
                }).collect(toList());
                reportMismatch(expected, filtered.size() == expected.size() ? filtered : actual);
            }
            return this;
        });
    }

    protected ResultValidator<R> expectOnly(Collection<?> expected, Collection<?> actual) {
        return fluxCapacitor.apply(fc -> {
            if (expected.size() != actual.size()) {
                reportMismatch(expected, actual);
            } else {
                if (!containsAll(expected, actual)) {
                    reportMismatch(expected, actual);
                }
            }
            return this;
        });
    }

    protected ResultValidator<R> expectNo(Collection<?> expectedNotToGet, Collection<?> actual) {
        return fluxCapacitor.apply(fc -> {
            if (containsAny(expectedNotToGet, actual)) {
                reportUnwantedMatch(expectedNotToGet, actual);
            }
            return this;
        });
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    protected void reportMismatch(Collection<?> expected, Collection<?> actual) {
        fluxCapacitor.apply(fc -> {
            if (!errors.isEmpty() && (actual.isEmpty() || !errors.containsAll(actual))) {
                throw new GivenWhenThenAssertionError(
                        "Published messages did not match. Probable cause is an exception that occurred during handling",
                        expected, actual, errors.getFirst());
            }
            throw new GivenWhenThenAssertionError("Published messages did not match", expected, actual);
        });
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    protected void reportUnwantedMatch(Collection<?> expected, Collection<?> actual) {
        fluxCapacitor.apply(fc -> {
            if (!errors.isEmpty() && (actual.isEmpty() || !errors.containsAll(actual))) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (errors.getFirst()));
            }
            throw new GivenWhenThenAssertionError(
                    format("Unwanted match found in published messages.\nExpected not to get: %s\nGot: %s\n\n",
                           expected, actual));
        });
    }

    protected ResultValidator<R> expectOnlyScheduledMessages(Collection<?> expected,
                                                          Collection<? extends Schedule> actual) {
        ResultValidator<R> result = expectScheduledMessages(expected, actual);
        return result.expectOnly(expected, actual);
    }

    protected boolean containsAll(Collection<?> expected, Collection<?> actual) {
        return expected.stream().allMatch(e -> actual.stream().anyMatch(a -> matches(e, a)));
    }

    protected boolean containsAny(Collection<?> expected, Collection<?> actual) {
        return expected.stream().anyMatch(e -> actual.stream().anyMatch(a -> matches(e, a)));
    }

    protected boolean matches(Object expected, Object actual) {
        if (actual instanceof Message) {
            return matches(expected, (Message) actual);
        }
        if (expected instanceof Predicate<?>) {
            return testSafely((Predicate<?>) expected, actual);
        }
        if (isMatcher(expected)) {
            return ((Matcher<?>) expected).matches(actual);
        }
        if (expected instanceof Class<?>) {
            return actual instanceof Class<?> ? expected.equals(actual) : ((Class<?>) expected).isInstance(actual);
        }
        return Objects.deepEquals(expected, actual);
    }

    protected boolean matches(Object expected, Message actual) {
        if (expected instanceof Predicate<?>) {
            return testSafely((Predicate<?>) expected, actual.getPayload()) || testSafely((Predicate<?>) expected,
                                                                                          actual);
        }
        if (isMatcher(expected)) {
            return ((Matcher<?>) expected).matches(actual.getPayload()) || ((Matcher<?>) expected).matches(actual);
        }
        if (expected instanceof Class<?>) {
            return ((Class<?>) expected).isInstance(actual.getPayload());
        }
        Message expectedMessage = asMessage(expected);
        if (actual instanceof Schedule && expected instanceof Schedule && !Objects.equals(
                ((Schedule) expected).getDeadline(), ((Schedule) actual).getDeadline())) {
            return false;
        }
        if (actual instanceof WebRequest && expected instanceof WebRequest && !Objects.equals(
                ((WebRequest) expected).getMethod(), ((WebRequest) actual).getMethod())) {
            return false;
        }
        if (actual instanceof WebResponse response && !(expected instanceof Message)) {
            Class<?> expectedType = expectedMessage.getPayloadClass();
            if (!response.getPayloadClass().equals(expectedType)) {
                return new EqualsBuilder().append(expected, response.getPayloadAs(expectedType)).isEquals();
            }
        }
        if (!actual.getMetadata().entrySet().containsAll(expectedMessage.getMetadata().entrySet())) {
            return false;
        }
        return new EqualsBuilder().append(expectedMessage.getPayload(), (Object) actual.getPayload()).isEquals();
    }

    protected Collection<?> asMessages(Object... expectedMessages) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> Arrays.stream(expectedMessages)
                .flatMap(e -> e instanceof Collection<?> ? ((Collection<?>) e).stream() : Stream.of(e))
                .map(c -> testFixture.parseObject(c, callerClass))
                .map(e -> e instanceof Message || e instanceof Predicate<?> || isMatcher(e) || e instanceof Class<?>
                        ? e : new Message(e)).collect(toList()));
    }

    @SuppressWarnings("unchecked")
    protected boolean testSafely(Predicate<?> predicate, Object actual) {
        try {
            return ((Predicate<Object>) predicate).test(actual);
        } catch (ClassCastException e) {
            return false;
        }
    }

    protected boolean isMatcher(Object expected) {
        return matchersSupported && expected instanceof Matcher<?>;
    }

    protected String describeException(Throwable e) {
        return Optional.ofNullable(e.getMessage())
                .map(m -> "%s: %s".formatted(e.getClass().getSimpleName(), m))
                .orElseGet(() -> e.getClass().getSimpleName());
    }

}
