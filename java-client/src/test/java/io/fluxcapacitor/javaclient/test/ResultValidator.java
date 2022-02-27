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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public class ResultValidator implements Then {
    private static final boolean matchersSupported = ReflectionUtils.classExists("org.hamcrest.Matcher");

    @Getter(AccessLevel.PROTECTED)
    private final FluxCapacitor fluxCapacitor;
    private final Object result;
    private final List<Message> events, commands, webRequests;
    private final List<Schedule> schedules;
    private final List<Throwable> exceptions;

    @Override
    public Then expectOnlyEvents(Object... events) {
        return expectOnly(asMessages(events), this.events);
    }

    @Override
    public Then expectEvents(Object... events) {
        return expect(asMessages(events), this.events);
    }

    @Override
    public Then expectNoEventsLike(Object... events) {
        return expectNothingLike(asMessages(events), this.events);
    }

    @Override
    public Then expectOnlyCommands(Object... commands) {
        return expectOnly(asMessages(commands), this.commands);
    }

    @Override
    public Then expectCommands(Object... commands) {
        return expect(asMessages(commands), this.commands);
    }

    @Override
    public Then expectNoCommandsLike(Object... commands) {
        return expectNothingLike(asMessages(commands), this.commands);
    }

    @Override
    public Then expectOnlySchedules(Object... schedules) {
        return expectOnlyScheduledMessages(asMessages(schedules), this.schedules);
    }

    @Override
    public Then expectSchedules(Object... schedules) {
        return expectScheduledMessages(asMessages(schedules), this.schedules);
    }

    @Override
    public Then expectNoSchedulesLike(Object... schedules) {
        return expectNothingLike(asMessages(schedules), this.schedules);
    }

    @Override
    public ResultValidator expectResult(Object expectedResult) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> {
            Object expected = TestFixture.parseObject(expectedResult, callerClass);
            if (result instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) result);
            }
            if (!matches(expected, result)) {
                if (isComparableToActual(expected)) {
                    throw new GivenWhenThenAssertionError(format(
                            "Handler returned a result of unexpected type.\nExpected: %s\nGot: %s",
                            expected.getClass(), result.getClass()));
                }
                throw new GivenWhenThenAssertionError("Handler returned an unexpected result",
                                                      expected, result);
            }
            return this;
        });
    }

    @Override
    public <T> Then expectResult(Predicate<T> predicate, String description) {
        return fluxCapacitor.apply(fc -> {
            if (result instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) result);
            }
            if (!testSafely(predicate, result)) {
                throw new GivenWhenThenAssertionError("Handler returned an unexpected result",
                                                      description, result);
            }
            return this;
        });
    }

    @Override
    public ResultValidator expectNoResultLike(Object value) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> {
            Object notExpected = TestFixture.parseObject(value, callerClass);
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
    public final <T> Then expectResultContaining(T... results) {
        if (!(this.result instanceof Collection<?>)) {
            throw new GivenWhenThenAssertionError("Result is not a collection", List.of(results), this.result);
        }
        return expect(List.of(results), (Collection<?>) this.result);
    }

    @Override
    public ResultValidator expectException(@NonNull Object expectedException) {
        return fluxCapacitor.apply(fc -> {
            if (!(result instanceof Throwable)) {
                throw new GivenWhenThenAssertionError(
                        "Handler returned normally but an exception was expected",
                        expectedException, result);
            }
            if (!matches(expectedException, result)) {
                if (isComparableToActual(expectedException)) {
                    throw new GivenWhenThenAssertionError(format(
                            "Handler threw unexpected exception.\nExpected: %s\nGot: %s",
                            expectedException.getClass(), result.getClass()));
                }
                throw new GivenWhenThenAssertionError("Handler threw unexpected exception",
                                                      expectedException, result);
            }
            return this;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Throwable> Then expectException(Predicate<T> predicate, String description) {
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
    public ResultValidator expectThat(Consumer<FluxCapacitor> check) {
        return fluxCapacitor.apply(fc -> {
            try {
                check.accept(fc);
            } catch (Throwable e) {
                if (!exceptions.isEmpty()) {
                    throw new GivenWhenThenAssertionError(String.format(
                            "Verify check failed: %s\nProbable cause is an exception during handling.", e.getMessage()),
                                                          (exceptions.get(0)));
                }
                throw new GivenWhenThenAssertionError("Verify check failed", e);
            }
            return this;
        });
    }

    @Override
    public Then expectTrue(Predicate<FluxCapacitor> check) {
        return fluxCapacitor.apply(fc -> {
            if (!check.test(fc)) {
                throw new GivenWhenThenAssertionError("Predicate test failed");
            }
            return this;
        });
    }

    protected boolean isComparableToActual(Object expected) {
        return result != null && expected != null && !isMatcher(expected)
                && !(expected instanceof Collection<?> && result instanceof Collection<?>)
                && !(expected instanceof Map<?, ?> && result instanceof Map<?, ?>)
                && !Objects.equals(expected.getClass(), result.getClass());
    }

    protected ResultValidator expectScheduledMessages(Collection<?> expected, Collection<? extends Schedule> actual) {
        return fluxCapacitor.apply(fc -> {
            if (!expected.isEmpty() && actual.isEmpty()) {
                throw new GivenWhenThenAssertionError("No messages were scheduled");
            }
            expected.forEach(e -> {
                if (e instanceof Schedule) {
                    if (actual.stream().noneMatch(s -> Objects.equals(s.getDeadline(), ((Schedule) e).getDeadline()))) {
                        throw new GivenWhenThenAssertionError(
                                format("Found no schedules with matching deadline. Expected %s. Got %s",
                                       ((Schedule) e).getDeadline(),
                                       actual.stream().map(Schedule::getDeadline).collect(toList())));
                    }
                }
            });
            return expect(asMessages(expected), actual);
        });
    }

    protected void reportMismatch(Collection<?> expected, Collection<?> actual) {
        fluxCapacitor.apply(fc -> {
            if (!exceptions.isEmpty()) {
                throw new GivenWhenThenAssertionError(
                        "Published messages did not match. Probable cause is an exception that occurred during handling",
                        expected, actual, exceptions.get(0));
            }
            throw new GivenWhenThenAssertionError("Published messages did not match", expected, actual);
        });
    }

    protected void reportUnwantedMatch(Collection<?> expected, Collection<?> actual) {
        fluxCapacitor.apply(fc -> {
            if (!exceptions.isEmpty()) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (exceptions.get(0)));
            }
            throw new GivenWhenThenAssertionError(
                    format("Unwanted match found in published messages.\nExpected not to get: %s\nGot: %s\n\n",
                           expected, actual));
        });
    }


    protected ResultValidator expect(Collection<?> expected, Collection<?> actual) {
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

    protected ResultValidator expectOnly(Collection<?> expected, Collection<?> actual) {
        if (expected.size() != actual.size()) {
            reportMismatch(expected, actual);
        } else {
            if (!containsAll(expected, actual)) {
                reportMismatch(expected, actual);
            }
        }
        return this;
    }

    protected ResultValidator expectNothingLike(Collection<?> expectedNotToGet, Collection<?> actual) {
        if (containsAny(expectedNotToGet, actual)) {
            reportUnwantedMatch(expectedNotToGet, actual);
        }
        return this;
    }

    protected ResultValidator expectOnlyScheduledMessages(Collection<?> expected,
                                                          Collection<? extends Schedule> actual) {
        ResultValidator result = expectScheduledMessages(expected, actual);
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
        return expectedMessage.getPayload().equals(actual.getPayload()) && actual.getMetadata().entrySet()
                .containsAll(expectedMessage.getMetadata().entrySet());
    }

    protected Collection<?> asMessages(Object... expectedMessages) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return fluxCapacitor.apply(fc -> Arrays.stream(expectedMessages)
                .flatMap(e -> e instanceof Collection<?> ? ((Collection<?>) e).stream() : Stream.of(e))
                .map(c -> TestFixture.parseObject(c, callerClass))
                .map(e -> e instanceof Message || e instanceof Predicate<?> || isMatcher(e) ? e :
                        new Message(e)).collect(toList()));
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

}
