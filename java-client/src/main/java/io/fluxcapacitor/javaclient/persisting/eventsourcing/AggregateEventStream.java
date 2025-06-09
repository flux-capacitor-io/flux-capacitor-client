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
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
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


    @NonNull
    @Override
    public Iterator<T> iterator() {
        return eventStream.iterator();
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return eventStream.filter(predicate);
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return eventStream.map(mapper);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return eventStream.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return eventStream.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return eventStream.mapToDouble(mapper);
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return eventStream.flatMap(mapper);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return eventStream.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return eventStream.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return eventStream.flatMapToDouble(mapper);
    }

    @Override
    public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
        return eventStream.mapMulti(mapper);
    }

    @Override
    public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> mapper) {
        return eventStream.mapMultiToInt(mapper);
    }

    @Override
    public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> mapper) {
        return eventStream.mapMultiToLong(mapper);
    }

    @Override
    public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> mapper) {
        return eventStream.mapMultiToDouble(mapper);
    }

    @Override
    public Stream<T> distinct() {
        return eventStream.distinct();
    }

    @Override
    public Stream<T> sorted() {
        return eventStream.sorted();
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return eventStream.sorted(comparator);
    }

    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        return eventStream.peek(action);
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return eventStream.limit(maxSize);
    }

    @Override
    public Stream<T> skip(long n) {
        return eventStream.skip(n);
    }

    @Override
    public Stream<T> takeWhile(Predicate<? super T> predicate) {
        return eventStream.takeWhile(predicate);
    }

    @Override
    public Stream<T> dropWhile(Predicate<? super T> predicate) {
        return eventStream.dropWhile(predicate);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        eventStream.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        eventStream.forEachOrdered(action);
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return eventStream.toArray();
    }

    @NotNull
    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return eventStream.toArray(generator);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return eventStream.reduce(identity, accumulator);
    }

    @NotNull
    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return eventStream.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return eventStream.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return eventStream.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return eventStream.collect(collector);
    }

    @Override
    public List<T> toList() {
        return eventStream.toList();
    }

    @NotNull
    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return eventStream.min(comparator);
    }

    @NotNull
    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return eventStream.max(comparator);
    }

    @Override
    public long count() {
        return eventStream.count();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return eventStream.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return eventStream.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return eventStream.noneMatch(predicate);
    }

    @NotNull
    @Override
    public Optional<T> findFirst() {
        return eventStream.findFirst();
    }

    @NotNull
    @Override
    public Optional<T> findAny() {
        return eventStream.findAny();
    }

    @NotNull
    @Override
    public Spliterator<T> spliterator() {
        return eventStream.spliterator();
    }

    @Override
    public boolean isParallel() {
        return eventStream.isParallel();
    }

    @NotNull
    @Override
    public Stream<T> sequential() {
        return eventStream.sequential();
    }

    @NotNull
    @Override
    public Stream<T> parallel() {
        return eventStream.parallel();
    }

    @NotNull
    @Override
    public Stream<T> unordered() {
        return eventStream.unordered();
    }

    @NotNull
    @Override
    public Stream<T> onClose(@NotNull Runnable closeHandler) {
        return eventStream.onClose(closeHandler);
    }

    @Override
    public void close() {
        eventStream.close();
    }
}
