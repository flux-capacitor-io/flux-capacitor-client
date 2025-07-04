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

import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link SnapshotStore} implementation that is used when snapshotting is explicitly disabled, such as when
 * {@code snapshotPeriod <= 0} is configured on an aggregate via {@link Aggregate}.
 *
 * @see SnapshotStore
 * @see Aggregate#snapshotPeriod()
 */
public enum NoOpSnapshotStore implements SnapshotStore {
    INSTANCE;

    @Override
    public <T> CompletableFuture<Void> storeSnapshot(Entity<T> snapshot) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> Optional<Entity<T>> getSnapshot(Object aggregateId) {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> deleteSnapshot(Object aggregateId) {
        return CompletableFuture.completedFuture(null);
    }
}
