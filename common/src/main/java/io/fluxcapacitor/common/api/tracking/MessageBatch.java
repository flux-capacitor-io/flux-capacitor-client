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

package io.fluxcapacitor.common.api.tracking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Value
public class MessageBatch {
    int[] segment;
    List<SerializedMessage> messages;
    Long lastIndex;
    Position position;

    public MessageBatch filter(Long minIndex, Long maxIndexExclusive) {
        if (messages.isEmpty() || (minIndex == null && maxIndexExclusive == null)) {
            return this;
        }
        Stream<SerializedMessage> messageStream = messages.stream();
        if (minIndex != null) {
            messageStream = messageStream.filter(m -> m.getIndex() >= minIndex);
        }
        if (maxIndexExclusive != null) {
            messageStream = messageStream.filter(m -> m.getIndex() < maxIndexExclusive);
        }
        var filteredMessages = messageStream.toList();
        var newLastIndex = lastIndex != null && maxIndexExclusive != null && maxIndexExclusive <= lastIndex ?
                Long.valueOf(maxIndexExclusive - 1L) : lastIndex;
        return new MessageBatch(segment, filteredMessages, newLastIndex, position);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    @JsonIgnore
    public int getSize() {
        return messages.size();
    }

    @Override
    public String toString() {
        return "MessageBatch{" +
               "segment=" + Arrays.toString(segment) +
               ", lastIndex=" + lastIndex +
               ", message count=" + messages.size() +
               '}';
    }

    @JsonIgnore
    public Metric toMetric() {
        return new Metric(segment, getSize(), lastIndex, position);
    }

    @Value
    public static class Metric {
        int[] segment;
        int size;
        Long lastIndex;
        Position position;
    }
}
