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

package io.fluxcapacitor.common.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.SearchUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class Document {
    @NonNull String id;
    String type;
    int revision;
    String collection;
    Instant timestamp, end;
    Map<Entry, List<Path>> entries;

    public Instant getEnd() {
        return end == null ? timestamp : end;
    }

    public String summarize() {
        return Stream.concat(Stream.of(type, id), entries.keySet().stream().map(Entry::asPhrase))
                .collect(Collectors.joining(" "));
    }

    public Optional<Entry> getEntryAtPath(String path) {
        Path pathObject = new Path(path);
        return entries.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(p -> Objects.equals(p, pathObject)))
                .findFirst().map(Map.Entry::getKey);
    }

    @Value
    @AllArgsConstructor
    public static class Entry implements Comparable<Entry> {
        EntryType type;
        String value;

        @JsonIgnore
        @Getter(lazy = true)
        @Accessors(fluent = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String asPhrase = computePhrase();

        @SuppressWarnings("ConstantConditions")
        private String computePhrase() {
            return type == EntryType.TEXT ? StringUtils.stripAccents(StringUtils.strip(value.toLowerCase())) : value;
        }

        @Override
        public int compareTo(@NonNull Entry o) {
            return getValue().compareTo(o.getValue());
        }
    }

    @Value
    public static class Path {
        String value;

        @JsonIgnore
        @Getter(lazy = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String shortValue = computeShortValue();

        private String computeShortValue() {
            return Arrays.stream(getValue().split("/"))
                    .filter(p -> !SearchUtils.isInteger(p)).collect(Collectors.joining("/"));
        }
    }

    public enum EntryType {
        TEXT(0), NUMERIC(1), BOOLEAN(2), NULL(3), EMPTY_ARRAY(4), EMPTY_OBJECT(5);

        private final byte index;

        EntryType(int index) {
            this.index = (byte) index;
        }

        public byte serialize() {
            return index;
        }

        public static EntryType deserialize(byte b) {
            switch (b) {
                case 0:
                    return TEXT;
                case 1:
                    return NUMERIC;
                case 2:
                    return BOOLEAN;
                case 3:
                    return NULL;
                case 4:
                    return EMPTY_ARRAY;
                case 5:
                    return EMPTY_OBJECT;
            }
            throw new IllegalArgumentException("Cannot convert to EntryType: " + b);
        }
    }
}