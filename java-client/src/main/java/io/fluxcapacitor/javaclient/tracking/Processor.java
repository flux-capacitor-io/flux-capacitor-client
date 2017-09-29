/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.ErrorHandler;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A processor keeps reading messages until it is stopped (generally only when the application is shut down).
 * <p>
 * A processor is always running in a single thread. To balance the processing load over multiple threads create
 * multiple processors with the same name but different channel (or use {@link #startMultiple}).
 * <p>
 * Processors with different names will receive the same messages. Processors with the same name will not.
 * (The messaging service will load balance between processors with the same name).
 * <p>
 * Processing stops if the provided message consumer throws an exception while handling messages (i.e. the processor
 * will need to be manually restarted in that case). However, if the processor encounters an exception while
 * fetching messages it will retry fetching until this succeeds.
 * <p>
 * Consumers can choose a desired maximum batch size for processing. By default this batch size will be the same as
 * the batch size the processor uses to fetch messages from the messaging service. Each time the consumer has finished
 * consuming a batch the processor will update its position with the messaging service.
 */
@Slf4j
public class Processor implements Runnable {

    private final String name;
    private final int channel;
    private final int maxFetchBatchSize;
    private final int maxWaitDuration;
    private final ConsumerService consumerService;
    private final Consumer<List<Message>> consumer;
    private final int maxConsumerBatchSize;
    private final Duration retryDelay;
    private final ErrorHandler<List<Message>> consumerErrorHandler;

    private volatile boolean running;

    public Processor(String name, ConsumerService consumerService, Consumer<List<Message>> consumer) {
        this(name, 0, consumerService, consumer);
    }

    public Processor(String name, int channel, ConsumerService consumerService, Consumer<List<Message>> consumer) {
        this(name, channel, 1024, Duration.ofMillis(10_000), consumerService, consumer,
             1024, Duration.ofSeconds(1),
             (e, batch) -> log.error("Consumer {} failed to handle batch {}", name, batch, e));
    }

    public Processor(String name, int channel, int maxFetchBatchSize, Duration maxWaitDuration,
                     ConsumerService consumerService, Consumer<List<Message>> consumer, int maxConsumerBatchSize,
                     Duration retryDelay, ErrorHandler<List<Message>> consumerErrorHandler) {
        this.name = name;
        this.channel = channel;
        this.maxFetchBatchSize = maxFetchBatchSize;
        this.maxWaitDuration = (int) maxWaitDuration.toMillis();
        this.consumerService = consumerService;
        this.consumer = consumer;
        this.maxConsumerBatchSize = maxConsumerBatchSize;
        this.retryDelay = retryDelay;
        this.consumerErrorHandler = consumerErrorHandler;
    }

    public static Registration startSingle(String name, ConsumerService consumerService,
                                           Consumer<List<Message>> consumer) {
        Processor processor = new Processor(name, consumerService, consumer);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(processor);
        return () -> {
            processor.stop();
            executor.shutdown();
            return true;
        };
    }

    public static Registration startMultiple(String name, int threads, ConsumerService consumerService,
                                             Consumer<List<Message>> consumer) {
        List<Processor> processors =
                IntStream.range(0, threads).mapToObj(i -> new Processor(name, i, consumerService, consumer)).collect(
                        Collectors.toList());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        processors.forEach(executor::submit);
        return () -> {
            processors.forEach(Processor::stop);
            executor.shutdown();
            return true;
        };
    }

    @Override
    public void run() {
        if (!running) {
            running = true;
        }
        while (running) {
            MessageBatch batch = fetch();
            process(batch.getMessages(), batch.getSegment());
        }
    }

    public void stop() {
        running = false;
    }

    protected MessageBatch fetch() {
        return retryOnFailure(
                () -> consumerService.read(name, channel, maxFetchBatchSize, maxWaitDuration, MILLISECONDS),
                retryDelay, e -> running);
    }

    protected void process(List<Message> messages, int[] segment) {
        if (messages.isEmpty() || !running) {
            return;
        }
        if (messages.size() > maxConsumerBatchSize) {
            for (int i = 0; i < messages.size(); i += maxConsumerBatchSize) {
                List<Message> batch = messages.subList( i, Math.min(i + maxConsumerBatchSize, messages.size()));
                processBatch(batch, segment);
            }
        } else {
            processBatch(messages, segment);
        }
    }

    protected void processBatch(List<Message> batch, int[] segment) {
        try {
            consumer.accept(batch);
        } catch (Exception e) {
            consumerErrorHandler.handleError(e, batch);
        }
        retryOnFailure(() -> {
            consumerService.storePosition(name, segment, batch.get(batch.size() - 1).getIndex());
            return null;
        }, retryDelay, e -> running);
    }
}
