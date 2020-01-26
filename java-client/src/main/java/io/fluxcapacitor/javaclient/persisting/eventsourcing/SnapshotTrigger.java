package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;

@FunctionalInterface
public interface SnapshotTrigger {

    boolean shouldCreateSnapshot(EventSourcedModel<?> model, List<DeserializingMessage> newEvents);

}
