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
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.Value;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Value
public class SimpleTrackerRead implements TrackerRead {
    String consumer;
    String trackerId;
    Long lastIndex;
    long deadline;
    Long purgeTimeout;
    int maxSize;
    Predicate<String> typeFilter;
    boolean ignoreMessageTarget;
    MessageType messageType;

    public SimpleTrackerRead(String consumer, String trackerId, Long previousLastIndex,
                             ConsumerConfiguration config) {
        this.consumer = consumer;
        this.trackerId = trackerId;
        this.lastIndex = previousLastIndex;
        this.deadline = System.currentTimeMillis() + config.getMaxWaitDuration().toMillis();
        this.purgeTimeout = Optional.ofNullable(config.getPurgeDelay()).map(Duration::toMillis).orElse(null);
        this.maxSize = config.getMaxFetchBatchSize();
        this.typeFilter = toPredicate(config.getTypeFilter());
        this.ignoreMessageTarget = config.ignoreMessageTarget();
        this.messageType = config.getMessageType();
    }

    private static Predicate<String> toPredicate(String typeFilter) {
        if (typeFilter == null) {
            return s -> true;
        }
        return Pattern.compile(typeFilter).asMatchPredicate();
    }

    public boolean canHandle(SerializedMessage message) {
        return message.getData().getType() == null || typeFilter.test(message.getData().getType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleTrackerRead that = (SimpleTrackerRead) o;
        return Objects.equals(consumer, that.consumer) &&
               Objects.equals(trackerId, that.trackerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumer, trackerId);
    }

    @Override
    public String toString() {
        return "WebSocketTracker{" +
               "consumerName='" + consumer + '\'' +
               ", trackerId='" + trackerId + '\'' +
               '}';
    }
}
