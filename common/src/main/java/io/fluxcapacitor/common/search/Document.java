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

package io.fluxcapacitor.common.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.search.Document.EntryType.NUMERIC;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class Document {
    public static Function<Document, ?> identityFunction = d -> String.format("%s_%s", d.getCollection(), d.getId());

    @NonNull String id;
    String type;
    int revision;
    String collection;
    Instant timestamp, end;
    Map<Entry, List<Path>> entries;
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    Supplier<String> summary;

    public Optional<Entry> getEntryAtPath(String path) {
        return getMatchingEntries(Path.pathPredicate(path)).findFirst();
    }

    public Stream<Entry> getMatchingEntries(Predicate<Path> pathPredicate) {
        return entries.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(pathPredicate))
                .map(Map.Entry::getKey);
    }

    public Document filterPaths(Predicate<Path> pathFilter) {
        Map<Entry, List<Path>> result = new LinkedHashMap<>();
        for (Map.Entry<Entry, List<Path>> e : entries.entrySet()) {
            List<Path> filtered = e.getValue().stream().filter(pathFilter).toList();
            if (!filtered.isEmpty()) {
                result.put(e.getKey(), filtered);
            }
        }
        return toBuilder().entries(result).build();
    }

    public Instant getEnd() {
        return end == null || timestamp == null || end.isAfter(timestamp) ? end : timestamp;
    }

    public static Comparator<Document> createComparator(SearchDocuments searchDocuments) {
        return searchDocuments.getSorting().stream().map(s -> {
            switch (s) {
                case "-timestamp":
                    return Comparator.comparing(Document::getTimestamp, Comparator.nullsFirst(naturalOrder())).reversed();
                case "timestamp":
                    return Comparator.comparing(Document::getTimestamp, Comparator.nullsFirst(naturalOrder()));
                case "-end":
                    return Comparator.comparing(Document::getEnd, Comparator.nullsLast(naturalOrder())).reversed();
                case "end":
                    return Comparator.comparing(Document::getEnd, Comparator.nullsLast(naturalOrder()));
                default:
                    boolean reversed = s.startsWith("-");
                    String path = reversed ? s.substring(1) : s;
                    Predicate<Path> pathPredicate = Path.pathPredicate(path);
                    Comparator<Document> valueComparator =
                            Comparator.nullsLast(Comparator.comparing(d -> {
                                Stream<Entry> matchingEntries = d.getMatchingEntries(pathPredicate);
                                return (reversed ? matchingEntries.max(naturalOrder()) :
                                        matchingEntries.min(naturalOrder())).orElse(null);
                            }, Comparator.nullsLast(naturalOrder())));
                    return reversed ? valueComparator.reversed() : valueComparator;
            }
        }).reduce(Comparator::thenComparing)
                .orElseGet(() -> Comparator.comparing(Document::getTimestamp, Comparator.nullsLast(naturalOrder())).reversed());
    }

    @Value
    @AllArgsConstructor
    public static class Entry implements Comparable<Entry> {
        EntryType type;
        String value;

        @JsonIgnore @Getter(lazy = true) @Accessors(fluent = true) @EqualsAndHashCode.Exclude @ToString.Exclude
        String asPhrase = getType() == EntryType.TEXT ? SearchUtils.normalize(getValue()) : getValue();

        @JsonIgnore @Getter(lazy = true) @Accessors(fluent = true) @EqualsAndHashCode.Exclude @ToString.Exclude
        BigDecimal asNumber = getType() == NUMERIC ? new BigDecimal(getValue()) : null;

        @Override
        public int compareTo(@NonNull Entry o) {
            if (type == NUMERIC && type == o.getType()) {
                return asNumber().compareTo(o.asNumber());
            }
            return getValue().compareToIgnoreCase(o.getValue());
        }
    }

    @Value
    public static class Path {
        public static final Pattern splitPattern = Pattern.compile("(?<!\\\\)/");
        public static final Pattern dotPattern = Pattern.compile("(?<!\\\\)\\.");

        private static final Function<String, String[]> splitFunction = memoize(in -> splitPattern.split(in));
        private static final Function<String, String> shortValueFunction = memoize(in -> split(in)
                .filter(p -> !SearchUtils.isInteger(p))
                .map(Path::unescapeFieldName)
                .collect(joining("/")));
        private static final Function<String, String> longValueFunction = memoize(in -> split(in)
                .map(Path::unescapeFieldName)
                .collect(joining("/")));

        public static Stream<String> split(String path) {
            return Arrays.stream(splitFunction.apply(path));
        }

        public static String escapeFieldName(String fieldName) {
            fieldName = fieldName.replace("/", "\\/");
            fieldName = fieldName.replace("\"", "\\\"");
            if (StringUtils.isNumeric(fieldName)) {
                try {
                    Integer.valueOf(fieldName);
                    fieldName = "\"" + fieldName + "\"";
                } catch (Exception ignored) {
                }
            }
            return fieldName;
        }

        public static String unescapeFieldName(String fieldName) {
            if (fieldName.startsWith("\"") && fieldName.endsWith("\"")) {
                fieldName = fieldName.substring(1, fieldName.length() - 1);
            }
            fieldName = fieldName.replace("\\/", "/");
            fieldName = fieldName.replace("\\\"", "\"");
            return fieldName;
        }

        public static Predicate<Path> pathPredicate(String path) {
            if (path == null) {
                return p -> true;
            }
            path = dotPattern.matcher(path).replaceAll("/");
            Predicate<String> predicate = SearchUtils.getGlobMatcher(path);
            return split(path).anyMatch(SearchUtils::isInteger)
                    ? p -> predicate.test(p.getLongValue()) : p -> predicate.test(p.getShortValue());
        }

        String value;

        @JsonIgnore
        @Getter(lazy = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String shortValue = shortValueFunction.apply(getValue());

        @JsonIgnore
        @Getter(lazy = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String longValue = longValueFunction.apply(getValue());

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
