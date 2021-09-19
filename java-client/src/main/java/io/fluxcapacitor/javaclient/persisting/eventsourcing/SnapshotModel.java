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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.function.UnaryOperator;

import static java.lang.String.format;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class SnapshotModel<T> implements AggregateRoot<T> {
    @JsonProperty
    String id;
    @JsonProperty
    Class<T> type;
    @JsonProperty
    @Builder.Default
    long sequenceNumber = -1L;
    @JsonProperty
    String lastEventId;
    @JsonProperty
    Long lastEventIndex;
    @JsonProperty
    @Builder.Default
    Instant timestamp = Instant.now();
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @ToString.Exclude
    T model;

    @Override
    public AggregateRoot<T> previous() {
        return null;
    }

    @Override
    public T get() {
        return model;
    }

    @Override
    public AggregateRoot<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException(format("Not allowed to apply a %s. The model is readonly.",
                eventMessage));
    }

    @Override
    public AggregateRoot<T> update(UnaryOperator<T> function) {
        throw new UnsupportedOperationException("Not allowed to apply the given function. The model is readonly.");
    }

    @Override
    public <E extends Exception> AggregateRoot<T> assertLegal(Object... commands) throws E {
        throw new UnsupportedOperationException();
    }
}
