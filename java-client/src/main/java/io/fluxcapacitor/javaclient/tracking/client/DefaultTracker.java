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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.BatchProcessingException;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.TrackingException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static io.fluxcapacitor.javaclient.tracking.BatchInterceptor.join;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Comparator.naturalOrder;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;

/**
 * A tracker keeps reading messages until it is stopped (generally only when the application is shut down).
 * <p>
 * A tracker is always running in a single thread. To balance the processing load over multiple threads create multiple
 * trackers with the same name but different tracker id.
 * <p>
 * Trackers with different names will receive the same messages. Trackers with the same name will not. (Flux Capacitor
 * will load balance between trackers with the same name).
 * <p>
 * Tracking stops if the provided message consumer throws an exception while handling messages (i.e. the tracker will
 * need to be manually restarted in that case). However, if the tracker encounters an exception while fetching messages
 * it will retry fetching indefinitely until this succeeds.
 * <p>
 * Trackers can choose a desired maximum batch size for consuming. By default this batch size will be the same as the
 * batch size the tracker uses to fetch messages from Flux Capacitor. Each time the consumer has finished consuming a
 * batch the tracker will update its position with Flux Capacitor.
 * <p>
 * Trackers can be configured to use batch interceptors. A batch interceptor manages the invocation of the message
 * consumer. It is therefore typically used to manage a database transaction around the invocation of the consumer. Note
 * that if the interceptor gives rise to an exception the tracker will be stopped.
 */
@Slf4j
public class DefaultTracker implements Runnable, Registration {

    private final Consumer<List<SerializedMessage>> consumer;
    private final Tracker tracker;
    private final Consumer<MessageBatch> processor;

    private final TrackingClient trackingClient;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Thread> thread = new AtomicReference<>();
    private final Duration retryDelay;
    private final Long maxIndexExclusive;
    private final Long minIndex;
    private volatile Long lastProcessedIndex;
    private volatile boolean processing;

    /**
     * Starts one or more trackers. Messages will be passed to the given consumer. Once the consumer is done the
     * position of the tracker is automatically updated.
     * <p>
     * Each tracker started is using a single thread. To track in parallel configure the number of trackers using {@link
     * ConsumerConfiguration}.
     */
    public static Registration start(Consumer<List<SerializedMessage>> consumer, ConsumerConfiguration config,
                                     Client client) {
        List<DefaultTracker> instances = IntStream.range(0, config.getThreads())
                .mapToObj(i -> new DefaultTracker(consumer, config, new Tracker(
                        config.prependApplicationName() ? format("%s_%s", client.name(), config.getName()) :
                                config.getName(), config.getTrackerIdFactory().apply(client), config, null),
                                                  client.getTrackingClient(config.getMessageType())))
                .collect(toList());
        ExecutorService executor = newFixedThreadPool(config.getThreads());
        instances.forEach(executor::execute);
        return () -> {
            instances.forEach(DefaultTracker::cancel);
            executor.shutdownNow();
        };
    }

    public static Registration start(Consumer<List<SerializedMessage>> consumer, ConsumerConfiguration config,
                                     TrackingClient trackingClient) {
        List<DefaultTracker> instances = IntStream.range(0, config.getThreads())
                .mapToObj(i -> new DefaultTracker(consumer, config, new Tracker(
                        config.getName(), UUID.randomUUID().toString(), config, null), trackingClient))
                .collect(toList());
        ExecutorService executor = newFixedThreadPool(config.getThreads());
        instances.forEach(executor::execute);
        return () -> {
            instances.forEach(DefaultTracker::cancel);
            executor.shutdownNow();
        };
    }

    private DefaultTracker(Consumer<List<SerializedMessage>> consumer, ConsumerConfiguration config, Tracker tracker,
                           TrackingClient trackingClient) {
        this.consumer = consumer;
        this.tracker = tracker;
        this.processor = join(config.getBatchInterceptors()).intercept(this::process, tracker);
        this.trackingClient = trackingClient;
        this.retryDelay = Duration.ofSeconds(1);
        this.lastProcessedIndex = ofNullable(config.getMinIndex()).map(i -> i - 1).orElse(null);
        this.minIndex = config.getMinIndex();
        this.maxIndexExclusive = config.getMaxIndexExclusive();
    }

    @Override
    public void run() {
        if (running.compareAndSet(false, true)) {
            Tracker.current.set(tracker);
            thread.set(currentThread());
            while (running.get()) {
                MessageBatch batch = fetch(lastProcessedIndex);
                if (batch != null) {
                    Tracker.current.set(tracker.withMessageBatch(batch));
                    processor.accept(batch);
                }
            }
            Tracker.current.remove();
        }
    }

    protected MessageBatch fetch(Long lastIndex) {
        return retryOnFailure(() -> trackingClient.readAndWait(tracker.getName(), tracker.getTrackerId(),
                                                               lastIndex, tracker.getConfiguration()),
                              retryDelay, e -> running.get());
    }

    protected void process(MessageBatch batch) {
        Long lastIndex = batch.getLastIndex();
        try {
            processing = true;
            if (batch.getMessages().isEmpty() || !running.get()) {
                return;
            }
            batch = filterBatchIfNeeded(batch);
            if (batch.getMessages().isEmpty()) {
                updatePosition(batch.getLastIndex(), batch.getSegment());
                return;
            }
            doProcess(consumer, batch);
        } finally {
            processing = false;
            if (isMaxIndexReached(lastIndex)) {
                cancel();
            }
        }
    }

    private boolean isMaxIndexReached(Long lastIndex) {
        return maxIndexExclusive != null && lastIndex != null && maxIndexExclusive <= lastIndex;
    }

    private MessageBatch filterBatchIfNeeded(MessageBatch batch) {
        if (shouldFilterBatch(batch)) {
            var filter = messageIndexFilter();
            var newLastIndex = isMaxIndexReached(batch.getLastIndex()) ? maxIndexExclusive - 1 :
                    minIndex == null ? batch.getLastIndex() : Math.max(minIndex, batch.getLastIndex());
            batch = new MessageBatch(batch.getSegment(),
                                     batch.getMessages().stream().filter(filter).collect(toList()),
                                     newLastIndex);
        }
        return batch;
    }

    private boolean shouldFilterBatch(MessageBatch batch) {
        return isMaxIndexReached(batch.getLastIndex()) || (minIndex != null && !batch.getMessages().isEmpty()
                && minIndex > batch.getMessages().get(0).getIndex());
    }

    private Predicate<SerializedMessage> messageIndexFilter() {
        Predicate<SerializedMessage> filter = i -> true;
        if (maxIndexExclusive != null) {
            filter = filter.and(i -> i.getIndex() < maxIndexExclusive);
        }
        if (minIndex != null) {
            filter = filter.and(i -> i.getIndex() >= minIndex);
        }
        return filter;
    }


    private void doProcess(Consumer<List<SerializedMessage>> consumer, MessageBatch messageBatch) {
        List<SerializedMessage> messages = messageBatch.getMessages();
        try {
            consumer.accept(messages);
        } catch (BatchProcessingException e) {
            log.error(
                    "Consumer {} failed to handle batch of {} messages at index {} and did not handle exception. "
                            + "Consumer will be updated to the last processed index and then stopped.",
                    tracker.getName(), messages.size(), e.getMessageIndex());
            updatePosition(messages.stream().map(SerializedMessage::getIndex)
                                   .filter(i -> e.getMessageIndex() != null && i != null
                                           && i < e.getMessageIndex())
                                   .max(naturalOrder()).orElse(null), messageBatch.getSegment());
            processing = false;
            cancel();
            return;
        } catch (Exception e) {
            log.error("Consumer {} failed to handle batch of {} messages and did not handle exception. "
                              + "Tracker will be stopped.", tracker.getName(), messages.size(), e);
            processing = false;
            cancel();
            return;
        }
        updatePosition(messageBatch.getLastIndex(), messageBatch.getSegment());
    }


    private void updatePosition(Long index, int[] segment) {
        if (index != null) {
            lastProcessedIndex = index;
            retryOnFailure(
                    () -> {
                        try {
                            trackingClient.storePosition(tracker.getName(), segment, index).await();
                        } catch (Exception e) {
                            throw new TrackingException(
                                    String.format("Failed to store position of segments %s for tracker %s to index %s",
                                                  Arrays.toString(segment), tracker, index), e);
                        }
                    }, retryDelay, e2 -> running.get());
        }
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void cancel() {
        if (running.compareAndSet(true, false)) {
            //wait for processing to complete
            if (processing) {
                while (processing) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        currentThread().interrupt();
                        return;
                    }
                }
            } else {
                //interrupt message fetching
                try {
                    thread.get().interrupt();
                } catch (Exception e) {
                    log.warn("Not allowed to cancel tracker {}", tracker.getName(), e);
                } finally {
                    thread.set(null);
                }
            }
            tracker.getConfiguration().getBatchInterceptors().forEach(i -> {
                try {
                    i.shutdown(tracker);
                } catch (Exception e) {
                    log.warn("Failed to stop batch interceptor {}", i, e);
                }
            });
        }
    }


}
