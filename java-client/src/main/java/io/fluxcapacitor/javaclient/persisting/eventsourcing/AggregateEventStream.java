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

/**
 * A wrapper around a stream of aggregate events, enriched with metadata such as the aggregate ID and the last known
 * sequence number.
 *
 * <p>This type is returned by {@link io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore#getEvents}
 * and provides access to the raw or deserialized event stream, along with information necessary for event-sourced state
 * reconstruction (like the latest known sequence number).
 *
 * @param <T> The type of event elements in the stream.
 */
@Getter
@AllArgsConstructor
public class AggregateEventStream<T> implements Stream<T> {

    /**
     * The underlying stream of events to which this stream delegates.
     */
    @Delegate
    private final Stream<T> eventStream;

    /**
     * The aggregate ID to which the events belong.
     */
    private final String aggregateId;

    /**
     * A supplier that lazily provides the last sequence number in the event stream, if known.
     */
    private final Supplier<Long> lastSequenceNumber;

    /**
     * Returns an iterator over the events in the stream. This method is overridden to ensure compatibility with the
     * {@link Stream} interface.
     */
    @NonNull
    @Override
    public Iterator<T> iterator() {
        return eventStream.iterator();
    }

    /**
     * Transforms the underlying stream of events using a given stream converter function. The returned instance retains
     * the same aggregate ID and sequence number metadata.
     *
     * @param streamConverter A function that converts the original stream to another stream type.
     * @param <O>             The output type of the converted stream.
     * @return A new {@code AggregateEventStream} wrapping the converted stream.
     */
    public <O> AggregateEventStream<O> convert(Function<Stream<T>, Stream<O>> streamConverter) {
        return new AggregateEventStream<>(streamConverter.apply(eventStream), aggregateId, lastSequenceNumber);
    }

    /**
     * Returns the last known sequence number from the event stream, if available.
     *
     * @return An {@link Optional} containing the sequence number, or empty if not set.
     */
    public Optional<Long> getLastSequenceNumber() {
        return Optional.ofNullable(lastSequenceNumber.get());
    }
}
