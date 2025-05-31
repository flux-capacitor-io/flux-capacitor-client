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
import io.fluxcapacitor.javaclient.modeling.AppliedEvent;
import io.fluxcapacitor.javaclient.modeling.Entity;

import java.util.List;

/**
 * A {@link SnapshotTrigger} implementation that never triggers snapshot creation.
 *
 * <p>This trigger is used by default when snapshotting is explicitly disabled, such as when
 * {@code snapshotPeriod <= 0} is configured on an aggregate via {@link Aggregate}.
 *
 * <p>For many aggregate types this is a suitable default.
 *
 * @see SnapshotTrigger
 * @see Aggregate#snapshotPeriod()
 */
public enum NoSnapshotTrigger implements SnapshotTrigger {
    INSTANCE;

    /**
     * Always returns {@code false}, indicating that a snapshot should never be created.
     *
     * @param model     The current aggregate instance (unused).
     * @param newEvents The list of newly applied events (unused).
     * @return {@code false} always.
     */
    @Override
    public boolean shouldCreateSnapshot(Entity<?> model, List<AppliedEvent> newEvents) {
        return false;
    }
}
