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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public class AggregateEventStream<T> implements Stream<T> {
    @Delegate
    private final Stream<T> eventStream;
    private final String aggregateId;
    private final Supplier<Long> lastSequenceNumber;

    @NonNull
    public Iterator<T> iterator() {
        return eventStream.iterator();
    }

    public <O> AggregateEventStream<O> convert(Function<Stream<T>, Stream<O>> streamConvertor) {
        return new AggregateEventStream<>(streamConvertor.apply(eventStream), aggregateId, lastSequenceNumber);
    }

    public Optional<Long> getLastSequenceNumber() {
        return Optional.ofNullable(lastSequenceNumber.get());
    }
}
