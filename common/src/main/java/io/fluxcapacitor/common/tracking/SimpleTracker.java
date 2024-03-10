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

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.Objects;
import java.util.function.Consumer;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class SimpleTracker implements Tracker {
    String consumerName;
    @Default
    int maxSize = 1024;
    Consumer<MessageBatch> handler;
    @Accessors(fluent = true)
    long maxTimeout = 6000;
    long deadline = System.currentTimeMillis() + maxTimeout;
    @With
    @Default
    Long lastTrackerIndex = 0L;

    public SimpleTracker(String consumerName, int maxSize, Consumer<MessageBatch> handler) {
        this(consumerName, maxSize, handler, 0L);
    }

    @Override
    public boolean ignoreSegment() {
        return false;
    }

    @Override
    public boolean clientControlledIndex() {
        return false;
    }

    @Override
    public String getTrackerId() {
        return consumerName;
    }

    @Override
    public String getClientId() {
        return consumerName;
    }

    @Override
    public Long getPurgeDelay() {
        return null;
    }

    @Override
    public void send(MessageBatch batch, Position position) {
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
        SimpleTracker that = (SimpleTracker) o;
        return Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerName);
    }

    @Override
    public String toString() {
        return "SimpleTracker{" +
               "consumerName='" + consumerName + '\'' +
               '}';
    }
}
