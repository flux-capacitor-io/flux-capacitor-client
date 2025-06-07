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
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.SortableEntry;
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

/**
 * Represents a structured, searchable, and indexable document within a Flux Capacitor collection.
 * <p>
 * A {@code Document} encapsulates metadata (such as ID, type, revision, timestamps) and document content structured as
 * a map of {@link Entry} values associated with one or more {@link Path}s.
 * <p>
 * Documents are typically constructed from user-defined data and then serialized via {@link SerializedDocument} for
 * indexing, querying, or audit purposes.
 *
 * <h2>Components</h2>
 * <ul>
 *   <li><b>entries</b>: the core field/value content of the document, including support for multiple path aliases</li>
 *   <li><b>facets</b>: fast lookup values, commonly used in {@link io.fluxcapacitor.common.api.search.FacetStats}</li>
 *   <li><b>indexes</b>: normalized and sortable values (e.g. timestamps, numbers) used for filtering and sorting</li>
 * </ul>
 *
 * @see SerializedDocument
 * @see Entry
 * @see Path
 * @see FacetEntry
 * @see SortableEntry
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class Document {
    public static Function<Document, ?> identityFunction = d -> String.format("%s_%s", d.getCollection(), d.getId());

    /**
     * Globally unique identifier for the document.
     */
    @NonNull
    String id;

    /**
     * Logical type of the document (may represent a domain concept or discriminator).
     */
    String type;

    /**
     * Revision number for version control or optimistic concurrency.
     */
    int revision;

    /**
     * Name of the collection in which this document resides.
     */
    String collection;

    /**
     * Timestamp at which the document was created or last updated.
     */
    Instant timestamp;

    /**
     * Optional end time for the document's lifecycle or temporal range.
     */
    Instant end;

    /**
     * Map of document entries to associated access paths.
     */
    Map<Entry, List<Path>> entries;

    /**
     * Optional string-based summary supplier for describing the document contents.
     */
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    Supplier<String> summary;

    /**
     * Set of fast-access facet values, used in facet-based filtering.
     */
    @Builder.Default
    Set<FacetEntry> facets = Collections.emptySet();

    /**
     * Set of sortable values used for range queries and ordering.
     */
    @Builder.Default
    Set<SortableEntry> sortables = Collections.emptySet();

    /**
     * Retrieves the first matching {@link Entry} for a given query path.
     */
    public Optional<Entry> getEntryAtPath(String queryPath) {
        return getMatchingEntries(Path.pathPredicate(queryPath)).findFirst();
    }

    /**
     * Retrieves a stream of entries that match the provided path predicate.
     */
    public Stream<Entry> getMatchingEntries(Predicate<Path> pathPredicate) {
        return entries.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(pathPredicate))
                .map(Map.Entry::getKey);
    }

    /**
     * Retrieves a stream of {@link SortableEntry} objects from the document's indexes that match the provided path
     * predicate.
     */
    public Stream<SortableEntry> getSortableEntries(Predicate<Path> pathPredicate) {
        return sortables.stream().filter(e -> pathPredicate.test(e.getPath()));
    }

    /**
     * Filters document entries by a {@link Path} predicate.
     */
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

    /**
     * Constructs a {@link Comparator} to sort documents based on the sorting options in {@link SearchDocuments}.
     */
    public static Comparator<Document> createComparator(SearchDocuments searchDocuments) {
        return searchDocuments.getSorting().stream().map(s -> {
                    switch (s) {
                        case "-timestamp":
                            return Comparator.comparing(Document::getTimestamp, Comparator.nullsFirst(naturalOrder()))
                                    .reversed();
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
                                        Stream<SortableEntry> matchingEntries = d.getSortableEntries(pathPredicate);
                                        return (reversed ? matchingEntries.max(naturalOrder()) :
                                                matchingEntries.min(naturalOrder())).orElse(null);
                                    }, Comparator.nullsLast(naturalOrder())));
                            return reversed ? valueComparator.reversed() : valueComparator;
                    }
                }).reduce(Comparator::thenComparing)
                .orElseGet(() -> Comparator.comparing(Document::getTimestamp, Comparator.nullsLast(naturalOrder()))
                        .reversed());
    }

    /**
     * Represents a typed field value within a document (e.g. text, number, boolean).
     * <p>
     * Each entry is associated with one or more paths in the document and may be normalized for search.
     */
    @Value
    @AllArgsConstructor
    public static class Entry implements Comparable<Entry> {
        /**
         * Specifies the type of the document entry value.
         */
        EntryType type;

        /**
         * Represents the value of an entry in a document. This value is typically a string representation that may need
         * to be interpreted depending on the entry type (e.g., as text or numeric). It is often used to store
         * normalized or processed data for search and comparison purposes.
         */
        String value;

        /**
         * A lazily evaluated string representation of the entry's value, suitable for phrase-based search or display.
         * If the entry's type is {@link EntryType#TEXT}, the value is normalized using
         * {@link SearchUtils#normalize(String)}. Otherwise, the raw value is returned.
         */
        @JsonIgnore @Getter(lazy = true) @Accessors(fluent = true) @EqualsAndHashCode.Exclude @ToString.Exclude
        String asPhrase = getType() == EntryType.TEXT ? SearchUtils.normalize(getValue()) : getValue();

        /**
         * A lazily evaluated numeric representation of the entry's value.
         * <p>
         * If the entry's type is {@link EntryType#NUMERIC}, the value is converted into a {@link BigDecimal}. If the
         * type is not numeric, this property is null.
         */
        @JsonIgnore @Getter(lazy = true) @Accessors(fluent = true) @EqualsAndHashCode.Exclude @ToString.Exclude
        BigDecimal asNumber = getType() == NUMERIC ? new BigDecimal(getValue()) : null;

        /**
         * Compares this entry with the specified entry for order. The comparison is primarily based on the type of the
         * entries. If both entries are numeric, their numeric values are compared. Otherwise, their string values are
         * compared case-insensitively.
         */
        @Override
        public int compareTo(@NonNull Entry o) {
            if (type == NUMERIC && type == o.getType()) {
                return asNumber().compareTo(o.asNumber());
            }
            return getValue().compareToIgnoreCase(o.getValue());
        }
    }

    /**
     * Represents a navigable field access path for a {@link Document} entry.
     * <p>
     * A {@code Path} is used to locate or filter entries within a document's hierarchical structure (e.g., nested
     * objects or arrays). It supports glob-based pattern matching and two normalized representations:
     * <ul>
     *     <li><strong>Short form</strong>: omits numeric indices used in list paths (e.g. {@code items/price})</li>
     *     <li><strong>Long form</strong>: includes all components, such as array indices (e.g. {@code items/0/price})</li>
     * </ul>
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * new Path("customer/addresses/1/city").getShortValue(); // "customer/addresses/city"
     * new Path("customer/addresses/1/city").getLongValue();  // "customer/addresses/1/city"
     * }</pre>
     *
     * <p>
     * Paths are typically normalized during search and comparison to ensure consistent behavior regardless of source data structure.
     *
     * @see Document
     * @see SearchUtils#normalizePath(String)
     */
    @Value
    public static class Path {

        /**
         * Singleton instance representing an empty path.
         */
        public static Path EMPTY_PATH = new Path("");

        private static final Pattern splitPattern = Pattern.compile("(?<!\\\\)/");

        private static final Function<String, String[]> splitFunction = memoize(in -> splitPattern.split(in));

        private static final Function<String, String> shortValueFunction = memoize(in -> Arrays.stream(
                splitPattern.split(in))
                .filter(p -> !SearchUtils.isInteger(p)).map(SearchUtils::unescapeFieldName).collect(joining("/")));

        /**
         * Splits a path string into individual elements (unescaped), using {@code /} as the delimiter.
         *
         * @param path path string to split
         * @return stream of path components (e.g. {@code ["foo", "bar", "name"]})
         */
        public static Stream<String> split(String path) {
            return Arrays.stream(splitFunction.apply(path));
        }

        /**
         * Determines whether a given path string qualifies as a "long path".
         * <p>
         * A path is considered long if any of its components are numeric (indicating array indices).
         *
         * @param queryPath path string to analyze
         * @return true if the path includes numeric segments, false otherwise
         */
        public static boolean isLongPath(String queryPath) {
            return split(queryPath).anyMatch(SearchUtils::isInteger);
        }

        /**
         * Constructs a predicate to match {@link Path} instances against the given glob-style path query.
         * <p>
         * Internally distinguishes between short and long paths for matching.
         *
         * @param queryPath a glob pattern such as {@code foo/name}
         * @return a predicate that matches paths by their normalized form
         */
        public static Predicate<Path> pathPredicate(String queryPath) {
            if (queryPath == null) {
                return p -> true;
            }
            queryPath = SearchUtils.normalizePath(queryPath);
            Predicate<String> predicate = SearchUtils.getGlobMatcher(queryPath);
            return isLongPath(queryPath)
                    ? p -> predicate.test(p.getLongValue()) : p -> predicate.test(p.getShortValue());
        }

        /**
         * Raw path string as provided in the document (e.g. {@code foo/bar/1/baz}).
         */
        String value;

        /**
         * Returns the short form of this path: a normalized version with numeric segments (e.g. array indices)
         * excluded.
         * <p>
         * Useful for matching semantic fields regardless of list position.
         */
        @JsonIgnore
        @Getter(lazy = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String shortValue = shortValueFunction.apply(getValue());

        /**
         * Returns the long form of this path: a fully unescaped version including list indices and other structure.
         */
        @JsonIgnore
        @Getter(lazy = true)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        String longValue = Arrays.stream(splitPattern.split(getValue())).map(SearchUtils::unescapeFieldName)
                .collect(joining("/"));

    }

    /**
     * Enumerates the supported types of values that can appear in a {@link Document} entry.
     * <p>
     * These types provide a normalized internal representation of different JSON-compatible values,
     * allowing for efficient indexing, filtering, and serialization within the Flux document store.
     *
     * <h2>Supported Types</h2>
     * <ul>
     *     <li>{@link #TEXT} – textual content, including strings and non-numeric identifiers</li>
     *     <li>{@link #NUMERIC} – numeric values such as integers, floats, or decimals</li>
     *     <li>{@link #BOOLEAN} – boolean values: {@code true} or {@code false}</li>
     *     <li>{@link #NULL} – explicitly null entries (i.e., JSON {@code null})</li>
     *     <li>{@link #EMPTY_ARRAY} – empty arrays (e.g. {@code []})</li>
     *     <li>{@link #EMPTY_OBJECT} – empty objects (e.g. {@code {}})</li>
     * </ul>
     *
     * <p>
     * Each entry type can be serialized into a compact {@code byte} identifier for efficient wire
     * transfer or storage. Use {@link #serialize()} and {@link #deserialize(byte)} to convert
     * between enum instances and their byte representation.
     *
     * @see Document.Entry
     * @see Document
     */
    public enum EntryType {

        /**
         * Represents textual values such as plain strings (e.g., {@code "hello"}).
         */
        TEXT(0),

        /**
         * Represents numeric values including integers and decimals (e.g., {@code 42}, {@code 3.14}).
         */
        NUMERIC(1),

        /**
         * Represents boolean values: {@code true} or {@code false}.
         */
        BOOLEAN(2),

        /**
         * Represents explicitly {@code null} entries.
         */
        NULL(3),

        /**
         * Represents empty arrays (e.g., {@code []}).
         */
        EMPTY_ARRAY(4),

        /**
         * Represents empty objects (e.g., {@code {}}).
         */
        EMPTY_OBJECT(5);

        private final byte index;

        EntryType(int index) {
            this.index = (byte) index;
        }

        /**
         * Serializes this entry type to a single byte.
         * <p>
         * Useful for compact storage or transmission over the wire.
         *
         * @return byte value representing this type
         */
        public byte serialize() {
            return index;
        }

        /**
         * Deserializes a byte into an {@link EntryType} enum instance.
         *
         * @param b the serialized byte value
         * @return corresponding {@link EntryType}
         * @throws IllegalArgumentException if the byte value is unrecognized
         */
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
