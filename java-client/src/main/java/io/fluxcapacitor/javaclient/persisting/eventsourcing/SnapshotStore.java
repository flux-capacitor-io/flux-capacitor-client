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

import io.fluxcapacitor.javaclient.modeling.Entity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing snapshots of aggregates in an event-sourced system.
 *
 * <p>Snapshots are serialized representations of an aggregate's state at a certain point in time (i.e., after a certain
 * sequence number). They allow the system to avoid replaying the entire event history for aggregates with large event logs.
 *
 * <p>This interface defines methods for storing, loading, and deleting such snapshots. Snapshots are typically
 * used in combination with {@link io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository} and
 * {@link io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore} to optimize aggregate loading.
 *
 * <p>Usage of this interface is optional—if no snapshot is found, the aggregate is reconstructed from its full event history.
 */
public interface SnapshotStore {

    /**
     * Stores a new snapshot for the given aggregate entity.
     *
     * <p>The snapshot typically contains the latest known state and metadata such as the aggregate ID and sequence number.
     * Storing a snapshot will overwrite any existing snapshot for the same aggregate ID.
     *
     * @param snapshot The aggregate entity to be stored as a snapshot.
     * @param <T>      The aggregate root type.
     * @return A {@link CompletableFuture} indicating whether the operation completed successfully.
     */
    <T> CompletableFuture<Void> storeSnapshot(Entity<T> snapshot);

    /**
     * Retrieves the most recent snapshot for a given aggregate ID, if available.
     *
     * @param aggregateId The ID of the aggregate for which to retrieve a snapshot.
     * @param <T>         The expected type of the aggregate.
     * @return An {@link Optional} containing the aggregate snapshot if present, otherwise empty.
     */
    <T> Optional<Entity<T>> getSnapshot(Object aggregateId);

    /**
     * Deletes the snapshot for the specified aggregate ID.
     *
     * @param aggregateId The ID of the aggregate whose snapshot should be deleted.
     * @return A {@link CompletableFuture} indicating completion of the deletion operation.
     */
    CompletableFuture<Void> deleteSnapshot(Object aggregateId);

}
