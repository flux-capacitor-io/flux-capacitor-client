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
import io.fluxcapacitor.common.api.search.FacetEntry;
import io.fluxcapacitor.common.api.search.IndexedEntry;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Builder.Default
    Set<FacetEntry> facets = Collections.emptySet();
    @Builder.Default
    Set<IndexedEntry> indexes = Collections.emptySet();

    public Optional<Entry> getEntryAtPath(String queryPath) {
        return getMatchingEntries(Path.pathPredicate(queryPath)).findFirst();
    }

    public Stream<Entry> getMatchingEntries(Predicate<Path> pathPredicate) {
        return entries.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(pathPredicate))
                .map(Map.Entry::getKey);
    }

    public Stream<IndexedEntry> getIndexedEntries(Predicate<Path> pathPredicate) {
        return indexes.stream().filter(e -> pathPredicate.test(e.getPath()));
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
                    String queryPath = reversed ? s.substring(1) : s;
                    Predicate<Path> pathPredicate = Path.pathPredicate(queryPath);
                    Comparator<Document> valueComparator =
                            Comparator.nullsLast(Comparator.comparing(d -> {
                                Stream<IndexedEntry> matchingEntries = d.getIndexedEntries(pathPredicate);
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
        public static Path EMPTY_PATH = new Path("");
        private static final Pattern splitPattern = Pattern.compile("(?<!\\\\)/");

        private static final Function<String, String[]> splitFunction = memoize(in -> splitPattern.split(in));
        private static final Function<String, String> shortValueFunction = memoize(in -> Arrays.stream(
                splitPattern.split(in))
                .filter(p -> !SearchUtils.isInteger(p)).map(SearchUtils::unescapeFieldName).collect(joining("/")));

        public static Stream<String> split(String path) {
            return Arrays.stream(splitFunction.apply(path));
        }

        public static boolean isLongPath(String queryPath) {
            return split(queryPath).anyMatch(SearchUtils::isInteger);
        }

        public static Predicate<Path> pathPredicate(String queryPath) {
            if (queryPath == null) {
                return p -> true;
            }
            queryPath = SearchUtils.normalizePath(queryPath);
            Predicate<String> predicate = SearchUtils.getGlobMatcher(queryPath);
            return isLongPath(queryPath)
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
        String longValue = Arrays.stream(splitPattern.split(getValue())).map(SearchUtils::unescapeFieldName)
                .collect(joining("/"));

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
            return switch (b) {
                case 0 -> TEXT;
                case 1 -> NUMERIC;
                case 2 -> BOOLEAN;
                case 3 -> NULL;
                case 4 -> EMPTY_ARRAY;
                case 5 -> EMPTY_OBJECT;
                default -> throw new IllegalArgumentException("Cannot convert to EntryType: " + b);
            };
        }
    }
}
