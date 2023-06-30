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

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchUtils {

    public static final String letterOrNumber = "\\p{L}0-9";
    public static final Pattern termPattern =
            Pattern.compile(String.format("\"[^\"]*\"|[%1$s][^\\s]*[%1$s]|[%1$s]", letterOrNumber), Pattern.MULTILINE);
    private static final Map<String, Pattern> globPatternCache = new ConcurrentHashMap<>();

    public static String normalize(@NonNull String text) {
        return StringUtils.stripAccents(text.trim().toLowerCase());
    }

    /**
     * Converts a standard POSIX Shell globbing pattern into a regular expression pattern. The result can be used with
     * the standard {@link java.util.regex} API to recognize strings which match the glob pattern.
     * <p>
     * From <a href="https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns">...</a>
     * <p>
     * See also, the POSIX Shell language: <a href="http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01">...</a>
     *
     * @param pattern A glob pattern.
     * @return A regex pattern to recognize the given glob pattern.
     */
    public static Pattern convertGlobToRegex(String pattern) {
        return globPatternCache.computeIfAbsent(pattern, p -> {
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
            return Pattern.compile(sb.toString());
        });
    }

    public static boolean isInteger(String fieldName) {
        if (StringUtils.isNumeric(fieldName)) {
            try {
                Integer.valueOf(fieldName);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static Object asIntegerOrString(String fieldName) {
        if (StringUtils.isNumeric(fieldName)) {
            try {
                return Integer.valueOf(fieldName);
            } catch (Exception ignored) {
            }
        }
        return fieldName;
    }

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
}
