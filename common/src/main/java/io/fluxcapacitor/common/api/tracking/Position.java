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

import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;

@Value
@AllArgsConstructor
public class Position {

    public static int MAX_SEGMENT = 128;
    public static int[] FULL_SEGMENT = new int[]{0, MAX_SEGMENT};

    public static Position newPosition() {
        return new Position(new HashMap<>());
    }

    Map<Integer, Long> indexBySegment;

    public Position(long index) {
        this(new int[]{0, MAX_SEGMENT}, index);
    }

    public Position(int[] segment, long index) {
        Map<Integer, Long> indexBySegment = new HashMap<>();
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

    public Position merge(Position newPosition) {
        Map<Integer, Long> indexBySegment = new HashMap<>(this.indexBySegment);
        newPosition.indexBySegment.forEach((s, i) -> indexBySegment.merge(s, i, (v, v2) -> v2.compareTo(v) > 0 ? v2 : v));
        return new Position(indexBySegment);
    }

    public Map<int[], Long> splitInSegments() {
        Map<int[], Long> result = new HashMap<>();
        new TreeMap<>(indexBySegment).entrySet().stream()
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
        Long index = indexBySegment.get(message.getSegment());
        return index == null || index < message.getIndex();
    }
}
