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

import io.fluxcapacitor.javaclient.modeling.AppliedEvent;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.util.List;

/**
 * A {@link SnapshotTrigger} that triggers snapshot creation at fixed intervals based on the aggregate's sequence number.
 *
 * <p>This is the default strategy used when a snapshot period is specified, e.g., via
 * {@code @Aggregate(snapshotPeriod = 100)}. It ensures that a snapshot is created every {@code period} events.
 *
 * <p>For example, if the period is 100:
 * <ul>
 *   <li>A snapshot is created after event 100 (sequence number 99)</li>
 *   <li>Then again after event 200, 300, and so on.</li>
 * </ul>
 *
 * <p>This is a useful strategy for reducing the number of events that need to be replayed when rehydrating
 * an aggregate, improving performance over time without incurring snapshot overhead after every event.
 *
 * @see io.fluxcapacitor.javaclient.modeling.Aggregate#snapshotPeriod()
 */
public class PeriodicSnapshotTrigger implements SnapshotTrigger {
    private final int period;

    /**
     * Constructs a periodic snapshot trigger with the given interval.
     *
     * @param period The number of events between snapshots. Must be ≥ 1.
     * @throws IllegalArgumentException if {@code period} is less than 1.
     */
    public PeriodicSnapshotTrigger(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period should be at least 1");
        }
        this.period = period;
    }

    /**
     * Determines whether a new snapshot should be created.
     * A snapshot is triggered if the current period index has increased compared to the period index before the
     * latest batch of events was applied.
     *
     * @param model      The current aggregate instance.
     * @param newEvents  The list of newly applied (but not yet committed) events.
     * @return {@code true} if a snapshot should be created, {@code false} otherwise.
     */
    @Override
    public boolean shouldCreateSnapshot(Entity<?> model, List<AppliedEvent> newEvents) {
        return periodIndex(model.sequenceNumber()) > periodIndex(model.sequenceNumber() - newEvents.size());
    }

    protected long periodIndex(long sequenceNumber) {
        return (sequenceNumber + 1) / period;
    }
}
