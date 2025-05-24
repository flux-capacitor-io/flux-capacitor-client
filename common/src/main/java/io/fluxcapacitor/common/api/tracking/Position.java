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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * Represents the tracking state of a consumer, i.e. the last known indexes of consumed messages per segment.
 * <p>
 * Flux Capacitor segments the message log to support parallel consumption. A {@code Position} stores
 * the most recent message index processed for each segment. This allows precise resumption of message
 * consumption on restarts or rebalances.
 *
 * <p>A {@code Position} is an immutable structure and may be merged or queried to support multi-segment tracking.
 */
@Value
@AllArgsConstructor
@JsonSerialize(using = Position.PositionSerializer.class)
@JsonDeserialize(using = Position.PositionDeserializer.class)
public class Position {

    private static final Position newPosition = new Position(new TreeMap<>());

    public static int MAX_SEGMENT = 128;
    public static int[] FULL_SEGMENT = new int[]{0, MAX_SEGMENT};

    /**
     * Creates an empty position.
     */
    public static Position newPosition() {
        return newPosition;
    }

    /**
     * Holds the last seen index per segment.
     */
    SortedMap<Integer, Long> indexBySegment;

    public Position(long index) {
        this(FULL_SEGMENT, index);
    }

    public Position(int[] segment, long index) {
        SortedMap<Integer, Long> indexBySegment = new TreeMap<>();
        IntStream.range(segment[0], segment[1]).forEach(i -> indexBySegment.put(i, index));
        this.indexBySegment = indexBySegment;
    }

    public Optional<Long> getIndex(int segment) {
        return Optional.ofNullable(indexBySegment.get(segment));
    }

    public boolean isNew(int[] segment) {
        return indexBySegment.entrySet().stream().filter(entry -> entry.getValue() != null)
                .noneMatch(entry -> entry.getKey() >= segment[0] && entry.getKey() < segment[1]);
    }

    public Optional<Long> lowestIndexForSegment(int[] segment) {
        int start = segment[0], end = segment[1];
        return indexBySegment.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> entry.getKey() >= start && entry.getKey() < end)
                .map(Map.Entry::getValue)
                .sorted().findFirst();
    }

    /**
     * Merges two {@code Position} objects by taking the highest known index per segment.
     */
    public Position merge(Position newPosition) {
        SortedMap<Integer, Long> indexBySegment = new TreeMap<>(this.indexBySegment);
        newPosition.indexBySegment.forEach((s, i) -> indexBySegment.merge(s, i, (v, v2) -> v2.compareTo(v) > 0 ? v2 : v));
        return new Position(indexBySegment);
    }

    /**
     * Splits the position map into contiguous segment ranges that share the same index.
     */
    public Map<int[], Long> splitInSegments() {
        Map<int[], Long> result = new LinkedHashMap<>();
        indexBySegment.entrySet().stream()
                .map(e -> Map.entry(new int[]{e.getKey(), e.getKey() + 1}, e.getValue())).reduce((a, b) -> {
            if (Objects.equals(a.getValue(), b.getValue()) && a.getKey()[1] == b.getKey()[0]) {
                return Map.entry(new int[]{a.getKey()[0], b.getKey()[1]}, b.getValue());
            }
            result.put(a.getKey(), a.getValue());
            return b;
        }).ifPresent(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public boolean isNewMessage(SerializedMessage message) {
        return isNewIndex(message.getSegment(), message.getIndex());
    }

    public boolean isNewIndex(int segment, Long messageIndex) {
        Long lastIndex = indexBySegment.get(segment);
        return lastIndex == null || messageIndex == null || lastIndex < messageIndex;
    }

    static class PositionSerializer extends JsonSerializer<Position> {
        @Override
        public void serialize(Position value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            for (Map.Entry<int[], Long> entry : value.splitInSegments().entrySet()) {
                gen.writeStartArray();
                gen.writeNumber(entry.getKey()[0]);
                gen.writeNumber(entry.getKey()[1]);
                gen.writeEndArray();
                gen.writeNumber(entry.getValue());
            }
            gen.writeEndArray();
        }
    }

    static class PositionDeserializer extends JsonDeserializer<Position> {
        private final TypeReference<SortedMap<Integer, Long>> mapTypeReference = new TypeReference<>() {};

        @Override
        public Position deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return switch (p.getCurrentToken()) {
                case START_OBJECT -> {
                    p.nextToken();
                    p.nextToken();
                    yield new Position(p.readValueAs(mapTypeReference));
                }
                case START_ARRAY -> {
                    Position position = Position.newPosition();
                    while (p.nextToken() == JsonToken.START_ARRAY) {
                        int[] range = new int[2];
                        p.nextToken();
                        range[0] = p.getIntValue();
                        p.nextToken();
                        range[1] = p.getIntValue();
                        p.nextToken();
                        p.nextToken();
                        long index = p.getLongValue();
                        position = position.merge(new Position(range, index));
                    }
                    yield position;
                }
                default -> throw new UnsupportedOperationException();
            };
        }
    }
}
