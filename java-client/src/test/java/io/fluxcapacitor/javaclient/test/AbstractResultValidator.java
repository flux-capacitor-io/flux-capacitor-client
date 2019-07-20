/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Collection;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public abstract class AbstractResultValidator implements Then {
    private final Object actualResult;

    protected Then expectMessages(Collection<?> expected, Collection<Message> actual) {
        if (!containsAll(expected, actual)) {
            reportMismatch(expected, actual);
        }
        return this;
    }

    protected Then expectOnlyMessages(Collection<?> expected, Collection<Message> actual) {
        if (expected.size() != actual.size()) {
            reportMismatch(expected, actual);
        } else {
            if (!containsAll(expected, actual)) {
                reportMismatch(expected, actual);
            }
        }
        return this;
    }

    protected Then expectNoMessagesLike(Collection<?> expectedNotToGet, Collection<Message> actual) {
        if (containsAny(expectedNotToGet, actual)) {
            reportUnwantedMatch(expectedNotToGet, actual);
        }
        return this;
    }

    protected void reportMismatch(Collection<?> expected, Collection<Message> actual) {
        if (actualResult instanceof Throwable) {
            throw new GivenWhenThenAssertionError(
                    "Published messages did not match. Probable cause is an exception that occurred during handling:",
                    (Throwable) actualResult);
        }
        throw new GivenWhenThenAssertionError(format("Published messages did not match.\nExpected: %s\nGot: %s\n\n", 
                                                       expected, actual) , expected, actual);
    }

    protected void reportUnwantedMatch(Collection<?> expected, Collection<Message> actual) {
        if (actualResult instanceof Throwable) {
            throw new GivenWhenThenAssertionError(
                    "An exception occurred during handling:",
                    (Throwable) actualResult);
        }
        throw new GivenWhenThenAssertionError(format("Unwanted match found in published messages.\nExpected not to get: %s\nGot: %s\n\n",
                expected, actual) , expected, actual);
    }
    
    protected boolean containsAll(Collection<?> expected, Collection<Message> actual) {
        return expected.stream().allMatch(e -> actual.stream().anyMatch(a -> matches(e, a)));
    }

    protected boolean containsAny(Collection<?> expected, Collection<Message> actual) {
        return expected.stream().anyMatch(e -> actual.stream().anyMatch(a -> matches(e, a)));
    }

    protected boolean matches(Object expected, Message actual) {
        if (expected instanceof Matcher<?>) {
            return ((Matcher<?>) expected).matches(actual.getPayload()) || ((Matcher<?>) expected).matches(actual);
        }
        Message expectedMessage = (Message) expected;
        return expectedMessage.getPayload().equals(actual.getPayload()) && actual.getMetadata().entrySet()
                .containsAll(expectedMessage.getMetadata().entrySet());
    }

    protected Collection<?> asMessages(Collection<?> events, MessageType type) {
        return events.stream().map(e -> e instanceof Matcher<?> ? (Matcher<?>) e :
                e instanceof Message ? (Message) e : new Message(e, type)).collect(toList());
    }

    @Override
    public Then expectResult(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (actualResult instanceof Throwable) {
            throw new GivenWhenThenAssertionError(format("Handler threw an unexpected exception. Expected: %s",
                                                         description), (Throwable) actualResult);
        }
        if (!resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler returned an unexpected value.\nExpected: %s\nGot: %s",
                                                         description, actualResult));
        }
        return this;
    }

    @Override
    public Then expectNoResultLike(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (actualResult instanceof Throwable) {
            throw new GivenWhenThenAssertionError(format("Handler threw an unexpected exception. Expected: %s",
                    description), (Throwable) actualResult);
        }
        if (resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler did return the unwanted result.\nExpected not to get: %s\nGot: %s",
                    description, actualResult));
        }
        return this;
    }

    @Override
    public Then verify(Runnable check) {
        try {
            check.run();
        } catch (Exception e) {
            throw new GivenWhenThenAssertionError("Verify check failed", e);
        }
        return this;
    }

    @Override
    public Then expectException(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (!(actualResult instanceof Throwable)) {
            throw new GivenWhenThenAssertionError(
                    format("Handler returned normally but an exception was expected. Expected: %s. Got: %s",
                           description, actualResult));
        }
        if (!resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler returned unexpected value.\nExpected: %s\nGot: %s",
                                                         description, actualResult));
        }
        return this;
    }
}
