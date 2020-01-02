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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;

/**
 * Utility that creates and starts one or more {@link DefaultTracker Trackers} of the same name and configuration. Each
 * tracker claims a single thread.
 */
public class TrackingUtils {

    public static Registration start(String consumerName, TrackingClient trackingClient,
                                     Consumer<List<SerializedMessage>> consumer) {
        return start(consumerName, consumer, trackingClient, TrackingConfiguration.DEFAULT);
    }

    public static Registration start(String consumerName, int threads, TrackingClient trackingClient,
                                     Consumer<List<SerializedMessage>> consumer) {
        return start(consumerName, consumer, trackingClient, TrackingConfiguration.builder().threads(threads).build());
    }

    public static Registration start(String consumerName, Consumer<List<SerializedMessage>> consumer,
                                     TrackingClient trackingClient, TrackingConfiguration configuration) {
        List<DefaultTracker> instances = IntStream.range(0, configuration.getThreads())
                .mapToObj(i -> new DefaultTracker(consumerName, configuration.getTrackerIdFactory().get(), 
                                                  configuration, consumer, trackingClient))
                .collect(toList());
        ExecutorService executor = newFixedThreadPool(configuration.getThreads());
        instances.forEach(executor::submit);
        return () -> {
            instances.forEach(DefaultTracker::cancel);
            executor.shutdownNow();
        };
    }
}
