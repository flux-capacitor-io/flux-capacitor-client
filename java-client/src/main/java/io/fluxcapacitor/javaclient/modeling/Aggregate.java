/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Aggregate<T> {

    String AGGREGATE_ID_METADATA_KEY = "$aggregateId";
    String AGGREGATE_TYPE_METADATA_KEY = "$aggregateType";

    T get();

    String lastEventId();

    Instant timestamp();

    Aggregate<T> previous();

    default Optional<Aggregate<T>> playBackToEvent(String eventId) {
        return playBackToCondition(aggregate -> Objects.equals(eventId, aggregate.lastEventId()));
    }

    default Optional<Aggregate<T>> playBackToCondition(Predicate<Aggregate<T>> condition) {
        Aggregate<T> result = this;
        while (result != null && !condition.test(result)) {
            result = result.previous();
        }
        return Optional.ofNullable(result);
    }

    Aggregate<T> apply(Message eventMessage);

    default Aggregate<T> apply(Object event) {
        return apply(new Message(event));
    }

    default Aggregate<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    default Aggregate<T> apply(Function<T, Message> eventFunction) {
        return apply(eventFunction.apply(get()));
    }


    default <E extends Exception> Aggregate<T> assertLegal(Object command) throws E {
        ValidationUtils.assertLegal(command, get());
        return this;
    }

    default <E extends Exception> Aggregate<T> assertThat(Validator<T, E> validator) throws E {
        validator.validate(this.get());
        return this;
    }

    default <E extends Exception> Aggregate<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
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