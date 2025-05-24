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

package io.fluxcapacitor.common.api.eventsourcing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command used to append one or more event batches to the event store.
 * <p>
 * Each {@link EventBatch} corresponds to a single aggregate instance and contains a list of serialized events along
 * with metadata such as the aggregate ID and the current sequence number.
 * <p>
 * This command is typically sent by a client when applying updates to aggregates in an event-sourced system. Events in
 * the same batch are stored atomically and will be assigned sequential positions in the event log.
 *
 * <h2>Routing</h2>
 * The command is routed based on the aggregate ID of the first batch. If multiple batches are included, they should
 * ideally refer to the same aggregate or be routed consistently by design.
 *
 * @see EventBatch
 * @see Guarantee
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class AppendEvents extends Command {

    /**
     * The event batches to append. Each batch contains events for a single aggregate and its metadata.
     */
    List<EventBatch> eventBatches;

    /**
     * Guarantee level for durability of this operation (e.g. persisted or replicated).
     */
    Guarantee guarantee;

    /**
     * Returns a human-readable description of the operation.
     */
    @Override
    public String toString() {
        return "AppendEvents of size " + eventBatches.size();
    }

    /**
     * Returns the routing key used to partition the command, based on the first batch’s aggregate ID.
     */
    @Override
    public String routingKey() {
        return eventBatches.getFirst().getAggregateId();
    }

    /**
     * Converts this command to a metric-friendly representation for logging and monitoring.
     */
    @Override
    public Metric toMetric() {
        return new Metric(eventBatches.stream().map(EventBatch::toMetric).collect(Collectors.toList()));
    }

    /**
     * Compact metric representation of an {@link AppendEvents} command.
     */
    @Value
    public static class Metric {
        List<EventBatch.Metric> eventBatches;
    }
}
