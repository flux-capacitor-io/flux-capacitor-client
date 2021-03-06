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

import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.SerializedDocumentUpdate;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.mockito.Mockito;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.test.GivenWhenThenUtils.toMatcher;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

@AllArgsConstructor
@Slf4j
public class ResultValidator implements Then {
    @Getter(AccessLevel.PROTECTED)
    private final FluxCapacitor fluxCapacitor;
    private final Object actualResult;
    private final List<Message> resultingEvents, resultingCommands;
    private final List<Schedule> resultingSchedules;

    @Override
    public Then expectOnlyEvents(List<?> events) {
        return expectOnly(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectEvents(List<?> events) {
        return expect(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectNoEventsLike(List<?> events) {
        return expectNothingLike(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectOnlyCommands(List<?> commands) {
        return expectOnly(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectCommands(List<?> commands) {
        return expect(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectNoCommandsLike(List<?> commands) {
        return expectNothingLike(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectOnlySchedules(List<?> schedules) {
        return expectOnlyScheduledMessages(asMessages(schedules), resultingSchedules);
    }

    @Override
    public Then expectSchedules(List<?> schedules) {
        return expectScheduledMessages(asMessages(schedules), resultingSchedules);
    }

    @Override
    public Then expectNoSchedulesLike(List<?> schedules) {
        return expectNothingLike(asMessages(schedules), resultingSchedules);
    }

    @Override
    public Then expectOnlyDocuments(List<?> documents) {
        return expectOnly(documents, getResultingDocuments());
    }

    @Override
    public Then expectDocuments(List<?> documents) {
        return expect(documents, getResultingDocuments());
    }

    @Override
    public Then expectNoDocumentsLike(List<?> documents) {
        return expectNothingLike(documents, getResultingDocuments());
    }

    @SuppressWarnings("unchecked")
    protected List<Object> getResultingDocuments() {
        return concat(Mockito.mockingDetails(fluxCapacitor.client().getSearchClient()).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("index"))
                .flatMap(i -> Arrays.stream(i.getArguments()).flatMap(a -> {
                    if (a instanceof Document[]) {
                        return Arrays.stream((Document[]) a);
                    }
                    if (a instanceof List<?>) {
                        return ((List<Document>) a).stream();
                    }
                    return Stream.empty();
                })).map(d -> fluxCapacitor.documentStore().getSerializer().fromDocument(d)),
        Mockito.mockingDetails(fluxCapacitor.client().getSearchClient()).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("bulkUpdate"))
                .flatMap(i -> Arrays.stream(i.getArguments()).flatMap(a -> {
                    if (a instanceof Collection<?>) {
                        return ((Collection<SerializedDocumentUpdate>) a).stream().map(SerializedDocumentUpdate::getObject)
                                .filter(Objects::nonNull).map(SerializedDocument::deserializeDocument);
                    }
                    return Stream.empty();
                })).map(d -> fluxCapacitor.documentStore().getSerializer().fromDocument(d)))
                .collect(toList());
    }

    @Override
    public ResultValidator expectResult(Object expectedResult) {
        return fluxCapacitor.apply(fc -> {
            if (actualResult instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) actualResult);
            }
            if (!matches(expectedResult, actualResult)) {
                if (!(expectedResult instanceof Matcher<?>) && actualResult != null && expectedResult != null
                        && !(expectedResult instanceof Collection<?> && actualResult instanceof Collection<?>)
                        && !(expectedResult instanceof Map<?, ?> && actualResult instanceof Map<?, ?>)
                        && !Objects.equals(expectedResult.getClass(), actualResult.getClass())) {
                    throw new GivenWhenThenAssertionError(format(
                            "Handler returned a result of unexpected type.\nExpected: %s\nGot: %s",
                            expectedResult.getClass(), actualResult.getClass()));
                }
                throw new GivenWhenThenAssertionError("Handler returned an unexpected result",
                                                      expectedResult, actualResult);
            }
            return this;
        });
    }

    @Override
    public ResultValidator expectNoResultLike(Object value) {
        return fluxCapacitor.apply(fc -> {
            if (actualResult instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) actualResult);
            }
            if (matches(value, actualResult)) {
                throw new GivenWhenThenAssertionError(
                        format("Handler returned the unwanted result.\nExpected not to get: %s\nGot: %s",
                               value, actualResult));
            }
            return this;
        });
    }

    @Override
    public ResultValidator expectThat(Consumer<FluxCapacitor> check) {
        return fluxCapacitor.apply(fc -> {
            try {
                check.accept(fc);
            } catch (Exception e) {
                throw new GivenWhenThenAssertionError("Verify check failed", e);
            }
            return this;
        });
    }

    @Override
    public ResultValidator expectException(Matcher<?> resultMatcher) {
        return fluxCapacitor.apply(fc -> {
            StringDescription description = new StringDescription();
            resultMatcher.describeTo(description);
            if (!(actualResult instanceof Throwable)) {
                throw new GivenWhenThenAssertionError(
                        "Handler returned normally but an exception was expected",
                        description, actualResult);
            }
            if (!resultMatcher.matches(actualResult)) {
                throw new GivenWhenThenAssertionError("Handler threw unexpected exception",
                                                      description, actualResult);
            }
            return this;
        });

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
            if (actualResult instanceof Throwable) {
                throw new GivenWhenThenAssertionError(
                        "Published messages did not match. Probable cause is an exception that occurred during handling:",
                        (Throwable) actualResult);
            }
            throw new GivenWhenThenAssertionError("Published messages did not match", expected, actual);
        });
    }

    protected void reportUnwantedMatch(Collection<?> expected, Collection<?> actual) {
        fluxCapacitor.apply(fc -> {
            if (actualResult instanceof Throwable) {
                throw new GivenWhenThenAssertionError("An unexpected exception occurred during handling",
                                                      (Throwable) actualResult);
            }
            throw new GivenWhenThenAssertionError(
                    format("Unwanted match found in published messages.\nExpected not to get: %s\nGot: %s\n\n",
                           expected, actual));
        });
    }


    protected ResultValidator expect(Collection<?> expected, Collection<?> actual) {
        if (!containsAll(expected, actual)) {
            List<?> remaining = new ArrayList<>(actual);
            List<?> filtered = expected.stream().flatMap(e -> {
                if (e != null && !(expected instanceof Matcher<?>) && !(expected instanceof Predicate<?>)) {
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
            expected = toMatcher((Predicate<?>) expected);
        }
        if (expected instanceof Matcher<?>) {
            return ((Matcher<?>) expected).matches(actual);
        }
        return Objects.equals(expected, actual);
    }

    protected boolean matches(Object expected, Message actual) {
        if (expected instanceof Predicate<?>) {
            expected = toMatcher((Predicate<?>) expected);
        }
        if (expected instanceof Matcher<?>) {
            return ((Matcher<?>) expected).matches(actual.getPayload()) || ((Matcher<?>) expected).matches(actual);
        }
        Message expectedMessage = expected instanceof Message ? (Message) expected : new Message(expected);
        return expectedMessage.getPayload().equals(actual.getPayload()) && actual.getMetadata().entrySet()
                .containsAll(expectedMessage.getMetadata().entrySet());
    }

    protected Collection<?> asMessages(Collection<?> expectedMessages) {
        return expectedMessages.stream().map(e -> e instanceof Predicate<?>
                ? GivenWhenThenUtils.toMatcher((Predicate<?>) e) : e instanceof Matcher<?> ? (Matcher<?>) e :
                e instanceof Message ? (Message) e : new Message(e)).collect(toList());
    }
}
