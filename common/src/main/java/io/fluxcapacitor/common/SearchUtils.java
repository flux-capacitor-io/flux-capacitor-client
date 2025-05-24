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

package io.fluxcapacitor.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Utility class for search-related functionality such as term normalization, path transformation, glob pattern
 * matching, and primitive value extraction.
 * <p>
 * This class is used extensively during document indexing, querying, and filtering.
 * It also includes utilities for path conversion between dot and slash notation, JSON normalization,
 * and field escaping/unescaping.
 */
public class SearchUtils {

    /**
     * Regex pattern for splitting dot-separated paths (ignoring escaped dots).
     */
    private static final Pattern dotPattern = Pattern.compile("(?<!\\\\)\\.");

    /**
     * Preloaded numeric strings mapped to their integer values, used for efficient parsing.
     */
    private static final Map<String, Integer> arrayIndices =
            IntStream.range(0, 1000).boxed().collect(toMap(Object::toString, identity()));

    /**
     * A character class used in regex patterns that includes letters and digits.
     */
    public static final String letterOrNumber = "\\p{L}0-9";

    /**
     * Pattern for extracting search terms and quoted phrases from a string.
     */
    public static final Pattern termPattern =
            Pattern.compile(String.format("\"[^\"]*\"|[%1$s][^\\s]*[%1$s]|[%1$s]", letterOrNumber), Pattern.MULTILINE);

    /**
     * Cache for compiled glob patterns.
     */
    private static final Map<String, Predicate<String>> globPatternCache = new ConcurrentHashMap<>();

    /**
     * Date-time formatter used to serialize or deserialize full ISO-8601 instant values with millisecond precision.
     */
    public static final DateTimeFormatter ISO_FULL
            = new DateTimeFormatterBuilder().parseCaseInsensitive().appendInstant(3).toFormatter();


    /**
     * Normalizes a string to lowercase and removes diacritics and leading/trailing whitespace.
     */
    public static String normalize(@NonNull String text) {
        return StringUtils.stripAccents(text).trim().toLowerCase();
    }

    /**
     * Normalizes all string values in a JSON byte array by stripping accents and lowercasing.
     * <p>
     * This is typically used during document indexing to enable consistent full-text search.
     *
     * @param data a raw JSON-encoded byte array
     * @return a normalized version of the input data
     */
    @SneakyThrows
    public static byte[] normalizeJson(byte[] data) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (JsonParser p = JsonUtils.writer.createParser(data);
             JsonGenerator generator = JsonUtils.writer.createGenerator(stream, JsonEncoding.UTF8)) {
            for (JsonToken token = p.nextToken(); token != null; token = p.nextToken()) {
                if (token == JsonToken.VALUE_STRING) {
                    generator.writeString(SearchUtils.normalize(p.getText()));
                } else {
                    generator.copyCurrentEventExact(p);
                }
            }
        }
        return stream.toByteArray();
    }

    /**
     * Converts a standard POSIX Shell globbing pattern into a regular expression pattern. The result can be used with
     * the standard {@link java.util.regex} API to recognize strings which match the glob pattern.
     * <p>
     * From <a
     * href="https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns">...</a>
     * <p>
     * See also, the POSIX Shell language: <a
     * href="http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01">...</a>
     *
     * @param pattern A glob pattern.
     * @return A regex pattern to recognize the given glob pattern.
     */
    public static Predicate<String> getGlobMatcher(String pattern) {
        return globPatternCache.computeIfAbsent(pattern, p -> tryGetSimpleGlobMatcher(pattern).orElseGet(() -> {
            StringBuilder sb = new StringBuilder(p.length());
            int inGroup = 0;
            int inClass = 0;
            int firstIndexInClass = -1;
            char[] arr = p.toCharArray();
            for (int i = 0; i < arr.length; i++) {
                char ch = arr[i];
                switch (ch) {
                    case '\\':
                        if (++i >= arr.length) {
                            sb.append('\\');
                        } else {
                            char next = arr[i];
                            switch (next) {
                                case ',':
                                    // escape not needed
                                    break;
                                case 'Q':
                                case 'E':
                                    // extra escape needed
                                    sb.append('\\');
                                default:
                                    sb.append('\\');
                            }
                            sb.append(next);
                        }
                        break;
                    case '*':
                        if (inClass == 0) {
                            sb.append(".*");
                        } else {
                            sb.append('*');
                        }
                        break;
                    case '?':
                        if (inClass == 0) {
                            sb.append('.');
                        } else {
                            sb.append('?');
                        }
                        break;
                    case '[':
                        inClass++;
                        firstIndexInClass = i + 1;
                        sb.append('[');
                        break;
                    case ']':
                        inClass--;
                        sb.append(']');
                        break;
                    case '.':
                    case '(':
                    case ')':
                    case '+':
                    case '|':
                    case '^':
                    case '$':
                    case '@':
                    case '%':
                        if (inClass == 0 || (firstIndexInClass == i && ch == '^')) {
                            sb.append('\\');
                        }
                        sb.append(ch);
                        break;
                    case '!':
                        if (firstIndexInClass == i) {
                            sb.append('^');
                        } else {
                            sb.append('!');
                        }
                        break;
                    case '{':
                        inGroup++;
                        sb.append('(');
                        break;
                    case '}':
                        inGroup--;
                        sb.append(')');
                        break;
                    case ',':
                        if (inGroup > 0) {
                            sb.append('|');
                        } else {
                            sb.append(',');
                        }
                        break;
                    default:
                        sb.append(ch);
                }
            }
            return Pattern.compile(sb.toString()).asMatchPredicate();
        }));
    }

    private static Optional<Predicate<String>> tryGetSimpleGlobMatcher(String pattern) {
        boolean postfix = pattern.endsWith("**");
        pattern = postfix ? pattern.substring(0, pattern.length() - 2) : pattern;
        boolean prefix = pattern.startsWith("**");
        String finalPattern = prefix ? pattern.substring(2) : pattern;
        for (char globCharacter : "*?{\\".toCharArray()) {
            if (finalPattern.indexOf(globCharacter) >= 0) {
                return Optional.empty();
            }
        }
        return postfix ?
                prefix ? Optional.of(s -> s.contains(finalPattern)) : Optional.of(s -> s.startsWith(finalPattern)) :
                prefix ? Optional.of(s -> s.endsWith(finalPattern)) : Optional.of(s -> s.equals(finalPattern));
    }

    /**
     * Checks whether the input string is a parseable integer.
     */
    public static boolean isInteger(String fieldName) {
        if (StringUtils.isNumeric(fieldName)) {
            try {
                Integer.parseInt(fieldName);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Attempts to convert a numeric string to an Integer, falling back to the original string otherwise.
     */
    public static Object asIntegerOrString(String fieldName) {
        if (StringUtils.isNumeric(fieldName)) {
            Object result = arrayIndices.get(fieldName);
            if (result != null) {
                return result;
            }
            try {
                return Integer.valueOf(fieldName);
            } catch (Exception ignored) {
            }
        }
        return fieldName;
    }

    /**
     * Extracts quoted phrases and standalone terms from a free-form query string.
     *
     * @param query the raw search string
     * @return a list of normalized search terms
     */
    public static List<String> splitInTerms(String query) {
        List<String> parts = new ArrayList<>();

        Matcher matcher = termPattern.matcher(query.trim());
        while (matcher.find()) {
            String group = matcher.group().trim();
            if (!group.isEmpty() && !group.equals("\"")) {
                if (group.startsWith("\"") && group.endsWith("\"")) {
                    group = group.substring(1, group.length() - 1);
                }
                parts.add(group);
            }
        }
        return parts;
    }

    /**
     * Converts any non-primitive value to its string form.
     */
    public static Object asPrimitive(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        return value.toString();
    }

    /**
     * Replaces unescaped dots in field paths with slashes.
     * <p>
     * This standardizes paths used in documents (e.g. {@code a.b.c} → {@code a/b/c}).
     */
    public static String normalizePath(String queryPath) {
        return queryPath == null ? null : dotPattern.matcher(queryPath).replaceAll("/");
    }

    /**
     * Escapes slashes and quotes in field names for safe indexing.
     */
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

    /**
     * Unescapes slashes and quotes in field names.
     */
    public static String unescapeFieldName(String fieldName) {
        if (fieldName.startsWith("\"") && fieldName.endsWith("\"")) {
            fieldName = fieldName.substring(1, fieldName.length() - 1);
        }
        fieldName = fieldName.replace("\\/", "/");
        fieldName = fieldName.replace("\\\"", "\"");
        return fieldName;
    }
}
