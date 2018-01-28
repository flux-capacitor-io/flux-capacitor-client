package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SnapshotSerializer {
    private final Serializer serializer;

    public SerializedSnapshot serialize(String aggregateId, long sequenceNumber, Object snapshot) {
        return new SerializedSnapshot(aggregateId, sequenceNumber, serializer.serialize(snapshot));
    }

    public <T> Snapshot<T> deserialize(SerializedSnapshot serializedSnapshot) {
        return new Snapshot<>(serializedSnapshot.getAggregateId(), serializedSnapshot.getLastSequenceNumber(),
                            serializer.deserialize(serializedSnapshot.data()));
    }
}
