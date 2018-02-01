package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
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
    public void storeSnapshot(Aggregate<?> snapshot) {
        try {
            keyValueClient.putValue(snapshotKey(snapshot.getId()), serializer.serialize(snapshot));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store a snapshot: %s", snapshot), e);
        }
    }

    @Override
    public <T> Optional<Aggregate<T>> getSnapshot(String aggregateId) {
        try {
            return Optional.ofNullable(keyValueClient.getValue(snapshotKey(aggregateId))).map(serializer::deserialize);
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
