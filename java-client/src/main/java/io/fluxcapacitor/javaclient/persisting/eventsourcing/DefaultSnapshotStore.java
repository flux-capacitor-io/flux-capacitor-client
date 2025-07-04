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

import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.Guarantee.STORED;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

/**
 * Default implementation of the {@link SnapshotStore} interface, responsible for managing snapshots of aggregate roots
 * in an event-sourced system.
 *
 * <p>This implementation uses a key-value store ({@link KeyValueClient}) to store snapshots, a {@link Serializer} to
 * handle serialization and deserialization of snapshots, and an {@link EventStore} for managing the associated event
 * data.
 *
 * <p>If deserialization fails, a warning is logged, the snapshot is deleted, and the aggregate is reconstructed from
 * its event history.
 */
@Slf4j
@AllArgsConstructor
public class DefaultSnapshotStore implements SnapshotStore {
    private final KeyValueClient keyValueClient;
    private final Serializer serializer;
    private final EventStore eventStore;

    @Override
    public <T> CompletableFuture<Void> storeSnapshot(Entity<T> snapshot) {
        try {
            return keyValueClient.putValue(snapshotKey(snapshot.id()), serializer.serialize(
                    ImmutableAggregateRoot.from(snapshot, null, null, eventStore)), STORED);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store a snapshot: %s", snapshot), e);
        }
    }

    @Override
    public <T> Optional<Entity<T>> getSnapshot(Object aggregateId) {
        try {
            return ofNullable(keyValueClient.getValue(snapshotKey(aggregateId))).map(serializer::deserialize);
        } catch (DeserializationException e) {
            log.warn("Failed to deserialize snapshot for {}. Deleting snapshot.", aggregateId, e);
            deleteSnapshot(aggregateId);
            return Optional.empty();
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain snapshot for aggregate %s", aggregateId), e);
        }
    }

    @Override
    public CompletableFuture<Void> deleteSnapshot(Object aggregateId) {
        try {
            return keyValueClient.deleteValue(snapshotKey(aggregateId), STORED);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to delete snapshot for aggregate %s", aggregateId), e);
        }
    }

    protected String snapshotKey(Object aggregateId) {
        return "$snapshot_" + aggregateId;
    }
}
