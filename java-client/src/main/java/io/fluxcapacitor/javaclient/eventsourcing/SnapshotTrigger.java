package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.util.List;

@FunctionalInterface
public interface SnapshotTrigger {

    boolean shouldCreateSnapshot(Aggregate<?> aggregate, List<Message> newEvents);

}
