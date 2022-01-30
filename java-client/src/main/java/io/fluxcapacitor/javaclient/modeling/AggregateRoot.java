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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.lang.String.format;

public interface AggregateRoot<T> {

    String AGGREGATE_ID_METADATA_KEY = "$aggregateId";
    String AGGREGATE_TYPE_METADATA_KEY = "$aggregateType";

    String id();

    Class<T> type();

    T get();

    String lastEventId();

    Long lastEventIndex();

    Instant timestamp();

    long sequenceNumber();

    AggregateRoot<T> previous();

    default AggregateRoot<T> playBackToEvent(String eventId) {
        return playBackToCondition(aggregate -> Objects.equals(eventId, aggregate.lastEventId()))
                .orElseThrow(() -> new IllegalStateException(format(
                        "Could not load aggregate %s of type %s for event %s. Aggregate (%s) started at event %s",
                        id(), type().getSimpleName(), eventId, this, lastEventId())));
    }

    default Optional<AggregateRoot<T>> playBackToCondition(Predicate<AggregateRoot<T>> condition) {
        AggregateRoot<T> result = this;
        while (result != null && !condition.test(result)) {
            result = result.previous();
        }
        return Optional.ofNullable(result);
    }

    default AggregateRoot<T> apply(Object... events) {
        return apply(List.of(events));
    }

    default AggregateRoot<T> apply(Collection<?> events) {
        AggregateRoot<T> result = this;
        for (Object event : events) {
            result = apply(event);
        }
        return result;
    }

    default AggregateRoot<T> apply(Object event) {
        if (event instanceof DeserializingMessage) {
            return apply(((DeserializingMessage) event).toMessage());
        }
        return apply(asMessage(event));
    }

    default AggregateRoot<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    AggregateRoot<T> apply(Message eventMessage);

    AggregateRoot<T> update(UnaryOperator<T> function);

    default <E extends Exception> AggregateRoot<T> assertLegal(Object command) throws E {
        if (command instanceof Collection<?>) {
            return assertLegal(((Collection<?>) command).toArray());
        }
        ValidationUtils.assertLegal(command, this);
        return this;
    }

    <E extends Exception> AggregateRoot<T> assertLegal(Object... commands) throws E;

    default <E extends Exception> AggregateRoot<T> assertThat(Validator<T, E> validator) throws E {
        validator.validate(this.get());
        return this;
    }

    default <E extends Exception> AggregateRoot<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        if (!check.test(get())) {
            throw errorProvider.apply(get());
        }
        return this;
    }

    @FunctionalInterface
    interface Validator<T, E extends Exception> {
        void validate(T model) throws E;
    }
}