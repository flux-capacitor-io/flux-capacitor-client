/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;

import java.util.Comparator;

public interface TrackerRead extends Comparable<TrackerRead> {
    Comparator<TrackerRead> comparator = Comparator.comparing(TrackerRead::getConsumer).thenComparing(TrackerRead::getTrackerId);

    MessageType getMessageType();

    String getConsumer();

    boolean canHandle(SerializedMessage message);

    String getTrackerId();

    Long getLastIndex();

    long getDeadline();

    Long getPurgeTimeout();

    int getMaxSize();

    @Override
    default int compareTo(TrackerRead o) {
        return comparator.compare(this, o);
    }
}
