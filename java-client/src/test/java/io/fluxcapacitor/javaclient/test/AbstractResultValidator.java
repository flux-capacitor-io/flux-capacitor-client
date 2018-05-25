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
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.Collection;
import java.util.Iterator;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public abstract class AbstractResultValidator implements Then {
    private final Object actualResult;
    
    protected Then expectMessages(Collection<Message> expected, Collection<Message> actual) {
        if (!containsAll(expected, actual)) {
            reportWrongMessages(expected, actual);
        }
        return this;
    }

    protected Then expectOnlyMessages(Collection<Message> expected, Collection<Message> actual) {
        if (!equals(expected, actual)) {
            reportWrongMessages(expected, actual);
        }
        return this;
    }
    
    protected void reportWrongMessages(Collection<Message> expected, Collection<Message> actual) {
        String message = format("Published messages did not match.\nExpected: %s\nGot: %s", expected, actual);
        if (actualResult instanceof Throwable) {
            message += "\nA probable cause is an exception that occurred during handling:";
            throw new GivenWhenThenAssertionError(message, (Throwable) actualResult);
        }
        throw new GivenWhenThenAssertionError(message);
    }

    protected boolean equals(Collection<Message> expected, Collection<Message> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Iterator<Message> actualIterator = actual.iterator();
        for (Message m : expected) {
            if (!equalsIgnoreNewMetadata(m, actualIterator.next())) {
                return false;
            }
        }
        return true;
    }

    protected boolean containsAll(Collection<Message> expected, Collection<Message> actual) {
        for (Message e : expected) {
            if (actual.stream().noneMatch(a -> equalsIgnoreNewMetadata(e, a))) {
                return false;
            }
        }
        return true;
    }

    protected boolean equalsIgnoreNewMetadata(Message expected, Message actual) {
        return expected.getPayload().equals(actual.getPayload()) && actual.getMetadata().entrySet()
                .containsAll(expected.getMetadata().entrySet());
    }

    protected Collection<Message> asMessages(Collection<?> events, MessageType type) {
        return events.stream().map(e -> e instanceof Message ? (Message) e : new Message(e, type)).collect(toList());
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
    public Then expectException(Matcher<?> resultMatcher) {
        StringDescription description = new StringDescription();
        resultMatcher.describeTo(description);
        if (!(actualResult instanceof Throwable)) {
            throw new GivenWhenThenAssertionError(format("Handler returned normally but an exception was expected. Expected: %s. Got: %s",
                                                         description, actualResult));
        }
        if (!resultMatcher.matches(actualResult)) {
            throw new GivenWhenThenAssertionError(format("Handler returned unexpected value.\nExpected: %s\nGot: %s",
                                                         description, actualResult));
        }
        return this;
    }
}
