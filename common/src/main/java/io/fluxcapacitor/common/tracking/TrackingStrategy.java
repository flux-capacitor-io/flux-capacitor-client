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

package io.fluxcapacitor.common.tracking;

import java.io.Closeable;
import java.util.function.Predicate;

public interface TrackingStrategy extends Closeable {

    void getBatch(Tracker tracker, PositionStore positionStore);

    void claimSegment(Tracker tracker, PositionStore positionStore);

    void disconnectTrackers(Predicate<Tracker> predicate, boolean sendFinalBatch);

    @Override
    void close();
}
