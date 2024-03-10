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

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;

import java.util.Comparator;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;

public interface Tracker extends Comparable<Tracker> {
    Comparator<Tracker> comparator = Comparator.comparing(Tracker::getConsumerName).thenComparing(Tracker::getTrackerId);

    String getConsumerName();

    default void sendEmptyBatch(MessageBatch batch) {
        send(batch, Position.newPosition());
    }

    void send(MessageBatch batch, Position position);

    default boolean canHandle(SerializedMessage message, int[] segmentRange) {
        return isValidTarget(message, segmentRange) && isValidType(message);
    }

    private boolean isValidTarget(SerializedMessage message, int[] segmentRange) {
        String target = message.getTarget();
        if (isFilterMessageTarget() && target != null) {
            if (target.equals(getTrackerId())) {
                return true; //skip #contains
            }
            if (!target.equals(getClientId())) {
                return false;
            }
        }
        return contains(message, segmentRange);
    }

    private boolean contains(SerializedMessage message, int[] segmentRange) {
        if (singleTracker()) {
            return segmentRange[0] == 0 && segmentRange[0] != segmentRange[1];
        }
        if (ignoreSegment()) {
            return true;
        }
        return segmentRange[1] != 0 && message.getSegment() >= segmentRange[0]
               && message.getSegment() < segmentRange[1];
    }

    private boolean isValidType(SerializedMessage message) {
        return message.getData().getType() == null || getTypeFilter().test(message.getData().getType());
    }

    String getClientId();

    default Predicate<String> getTypeFilter() {
        return s -> true;
    }

    default boolean singleTracker() {
        return false;
    }

    default boolean isFilterMessageTarget() {
        return false;
    }

    boolean ignoreSegment();

    boolean clientControlledIndex();

    String getTrackerId();

    Long getLastTrackerIndex();

    long getDeadline();

    long maxTimeout();

    default boolean hasMissedDeadline() {
        return isMissedDeadline(getDeadline());
    }

    Long getPurgeDelay();

    int getMaxSize();

    Tracker withLastTrackerIndex(Long lastTrackerIndex);

    @Override
    default int compareTo(Tracker o) {
        return comparator.compare(this, o);
    }
}
