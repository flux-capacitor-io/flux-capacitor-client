package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.util.List;

@FunctionalInterface
public interface SnapshotTrigger {

    boolean shouldCreateSnapshot(EventSourcedModel<?> model, List<Message> newEvents);

}
