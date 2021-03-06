/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static java.lang.String.format;

@Slf4j
@AllArgsConstructor
public class DefaultSnapshotRepository implements SnapshotRepository {

    private final KeyValueClient keyValueClient;
    private final Serializer serializer;

    @Override
    public <T> void storeSnapshot(EventSourcedModel<T> snapshot) {
        try {
            keyValueClient.putValue(snapshotKey(snapshot.id()), Optional.of(snapshot)
                    .map(s -> new SnapshotModel<>(s.id(), s.type(), s.sequenceNumber(), s.lastEventId(),
                            s.lastEventIndex(), s.timestamp(), s.model()))
                    .map(serializer::serialize).orElse(null), Guarantee.SENT);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store a snapshot: %s", snapshot), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<EventSourcedModel<T>> getSnapshot(String aggregateId) {
        try {
            return Optional.ofNullable(keyValueClient.getValue(snapshotKey(aggregateId)))
                    .map(serializer::deserialize).map(s -> (SnapshotModel<T>) s)
                    .map(s -> new EventSourcedModel<>(s.id(), s.type(), s.sequenceNumber(), s.lastEventId(),
                            s.lastEventIndex(), s.timestamp(), s.model(), null));
        } catch (SerializationException e) {
            log.warn("Failed to deserialize snapshot for {}. Deleting snapshot.", aggregateId, e);
            deleteSnapshot(aggregateId);
            return Optional.empty();
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain snapshot for aggregate %s", aggregateId), e);
        }
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        try {
            keyValueClient.deleteValue(snapshotKey(aggregateId));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to delete snapshot for aggregate %s", aggregateId), e);
        }
    }

    protected String snapshotKey(String aggregateId) {
        return "$snapshot_" + aggregateId;
    }
}
