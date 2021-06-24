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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.TrackingStrategy;
import io.fluxcapacitor.javaclient.tracking.client.TrackerRead;
import lombok.Value;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Value
public class WebSocketTrackerRead implements TrackerRead {
    String consumerName;
    String clientId;
    String sessionId;
    String trackerId;
    Long lastTrackerIndex;
    long deadline;
    Long purgeDelay;
    int maxSize;
    Predicate<String> typeFilter;
    boolean ignoreMessageTarget;
    TrackingStrategy strategy;
    MessageType messageType;

    public WebSocketTrackerRead(Read read, String clientId, String sessionId, MessageType messageType) {
        this.consumerName = read.getConsumer();
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.trackerId = read.getTrackerId();
        this.lastTrackerIndex = read.getLastIndex();
        this.deadline = System.currentTimeMillis() + read.getMaxTimeout();
        this.purgeDelay = read.getPurgeTimeout();
        this.maxSize = read.getMaxSize();
        this.typeFilter = toPredicate(read.getTypeFilter());
        this.ignoreMessageTarget = read.isIgnoreMessageTarget();
        this.strategy = read.getStrategy();
        this.messageType = messageType;
    }

    private static Predicate<String> toPredicate(String typeFilter) {
        if (typeFilter == null) {
            return s -> true;
        }
        return Pattern.compile(typeFilter).asMatchPredicate();
    }

    public boolean canHandle(SerializedMessage message) {
        return (ignoreMessageTarget || message.getTarget() == null || clientId.equals(message.getTarget()))
                && (message.getData().getType() == null || typeFilter.test(message.getData().getType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebSocketTrackerRead that = (WebSocketTrackerRead) o;
        return Objects.equals(consumerName, that.consumerName) &&
                Objects.equals(trackerId, that.trackerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerName, trackerId);
    }

    @Override
    public String toString() {
        return "WebSocketTracker{" +
                "consumerName='" + consumerName + '\'' +
                ", clientId='" + clientId + '\'' +
                ", trackerId='" + trackerId + '\'' +
                '}';
    }
}
