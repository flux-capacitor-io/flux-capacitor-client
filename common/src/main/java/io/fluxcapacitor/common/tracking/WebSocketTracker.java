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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Read;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@Getter
@AllArgsConstructor
public class WebSocketTracker implements Tracker {
    private static final Function<String, Predicate<String>> typeFilterCache = memoize(
            s -> s == null ? __ -> true : Pattern.compile(s).asMatchPredicate());

    private final String consumerName;
    private final MessageType messageType;
    private final String clientId;
    private final String sessionId;
    private final String trackerId;
    @With private final Long lastTrackerIndex;
    private final long deadline;
    private final Long purgeDelay;
    private final int maxSize;
    private final Predicate<String> typeFilter;
    private final boolean filterMessageTarget;
    @Accessors(fluent = true)
    private final boolean ignoreSegment;
    @Accessors(fluent = true)
    private final boolean clientControlledIndex;
    @Accessors(fluent = true)
    private final boolean singleTracker;
    @Accessors(fluent = true)
    private final long maxTimeout;

    private final Consumer<MessageBatch> handler;

    public WebSocketTracker(Read read, MessageType messageType, String clientId,
                            String sessionId, Consumer<MessageBatch> handler) {
        this.consumerName = read.getConsumer();
        this.messageType = messageType;
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.trackerId = read.getTrackerId();
        this.lastTrackerIndex = read.getLastIndex();
        this.maxTimeout = read.getMaxTimeout();
        this.deadline = System.currentTimeMillis() + read.getMaxTimeout();
        this.purgeDelay = read.getPurgeTimeout();
        this.maxSize = read.getMaxSize();
        this.typeFilter = typeFilterCache.apply(read.getTypeFilter());
        this.filterMessageTarget = read.isFilterMessageTarget();
        this.ignoreSegment = read.isIgnoreSegment() || messageType == MessageType.NOTIFICATION;
        this.clientControlledIndex = read.isClientControlledIndex() || messageType == MessageType.NOTIFICATION;
        this.singleTracker = read.isSingleTracker();
        this.handler = handler;
    }

    @Override
    public void send(MessageBatch batch) {
        handler.accept(batch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebSocketTracker that = (WebSocketTracker) o;
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
