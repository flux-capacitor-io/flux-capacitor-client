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

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.util.List;

public class PeriodicSnapshotTrigger implements SnapshotTrigger {
    private final int period;

    public PeriodicSnapshotTrigger(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period should be at least 1");
        }
        this.period = period;
    }

    @Override
    public boolean shouldCreateSnapshot(Entity<?> model, List<DeserializingMessage> newEvents) {
        return periodIndex(model.sequenceNumber()) > periodIndex(model.sequenceNumber() - newEvents.size());
    }

    protected long periodIndex(long sequenceNumber) {
        return (sequenceNumber + 1) / period;
    }
}
