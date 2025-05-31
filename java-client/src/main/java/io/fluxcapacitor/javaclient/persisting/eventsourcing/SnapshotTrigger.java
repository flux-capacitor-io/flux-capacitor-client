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
 * Functional interface used to determine whether a new snapshot should be created for an aggregate.
 *
 * <p>Implementations of this interface are consulted during the commit of new events to decide whether a snapshot of
 * the aggregate's current state should be persisted to the {@link SnapshotStore}.
 *
 * <p>This decision can be based on factors such as the number or type of events since the last snapshot, aggregate
 * version, or specific domain rules.
 *
 * <p>Snapshotting can improve performance for aggregates with long event histories by reducing the number of events
 * that need to be replayed during reconstruction.
 *
 * <p>Typical usage involves providing a custom implementation of this interface to an
 * {@link io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository} or another aggregate loader mechanism.
 *
 * @see SnapshotStore
 * @see Entity
 * @see AppliedEvent
 */
@FunctionalInterface
public interface SnapshotTrigger {

    /**
     * Determines whether a snapshot should be created for the given aggregate state and recent events.
     *
     * @param model     The current {@link Entity} representing the aggregate's state.
     * @param newEvents A list of {@link AppliedEvent}s that have just been applied to the aggregate
     *                  and are about to be committed.
     * @return {@code true} if a snapshot should be created, {@code false} otherwise.
     */
    boolean shouldCreateSnapshot(Entity<?> model, List<AppliedEvent> newEvents);
}
