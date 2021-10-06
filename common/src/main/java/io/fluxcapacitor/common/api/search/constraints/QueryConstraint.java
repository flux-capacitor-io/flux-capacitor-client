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

package io.fluxcapacitor.common.api.search.constraints;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.api.search.constraints.AllConstraint.all;
import static io.fluxcapacitor.common.api.search.constraints.ContainsConstraint.letterOrNumber;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Value
public class QueryConstraint extends PathConstraint {
    private static final String operator = "&|()!";
    private static final Pattern termPattern =
            Pattern.compile(String.format("\"[^\"]*\"|[%1$s]|[*%2$s][^\\s%1$s]+[*%2$s]|[*%2$s]+", operator, letterOrNumber), Pattern.MULTILINE);
    private static final Pattern splitOnInnerAsterisk = Pattern.compile(String.format("(?<=[%1$s])\\*(?=[%1$s])", letterOrNumber));

    public static Constraint query(String query, String... paths) {
        if (isBlank(query)) return noOp;
        switch (paths.length) {
            case 0:
                return new QueryConstraint(query, null);
            case 1:
                return new QueryConstraint(query, paths[0]);
            default:
                return new AnyConstraint(Arrays.stream(paths).map(p -> new QueryConstraint(query, p)).collect(
                        Collectors.toList()));
        }
    }


    @NonNull String query;
    String path;

    @Override
    public boolean matches(Document document) {
        return decompose().matches(document);
    }

    @Override
    protected boolean matches(Document.Entry entry) {
        throw new UnsupportedOperationException();
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Constraint decompose = AllConstraint.all(createConstraints(splitInTermsAndOperators(query)));

    private List<Constraint> createConstraints(List<String> parts) {
        List<Constraint> result = new ArrayList<>();
        ListIterator<String> iterator = parts.listIterator();
        while (iterator.hasNext()) {
            parsePart(iterator.next(), iterator, result);
        }
        return result;
    }

    private void parsePart(String part, ListIterator<String> iterator, List<Constraint> constraints) {
        switch (part) {
            case "(":
                handleGroupStart(iterator, constraints);
                break;
            case "|":
                handleOr(iterator, constraints);
                break;
            case "!":
                handleNot(iterator, constraints);
                break;
            case "":
                break;
            default:
                handleTerm(part, constraints);
                break;
        }
    }

    private void handleGroupStart(ListIterator<String> iterator, List<Constraint> constraints) {
        List<Constraint> subList = new ArrayList<>();
        while (iterator.hasNext()) {
            String part = iterator.next();
            if (part.equals(")")) {
                break;
            }
            parsePart(part, iterator, subList);
        }
        constraints.add(AllConstraint.all(subList));
    }

    private void handleOr(ListIterator<String> iterator, List<Constraint> constraints) {
        if (iterator.hasNext() && !constraints.isEmpty()) {
            Constraint leftHandConstraint = constraints.remove(constraints.size() - 1);
            List<Constraint> rightHandPart = new ArrayList<>();
            parsePart(iterator.next(), iterator, rightHandPart);
            constraints.add(leftHandConstraint.or(AllConstraint.all(rightHandPart)));
        } else {
            parsePart("OR", iterator, constraints);
        }
    }

    private void handleNot(ListIterator<String> iterator, List<Constraint> constraints) {
        List<Constraint> subList = new ArrayList<>();
        if (iterator.hasNext()) {
            parsePart(iterator.next(), iterator, subList);
        }
        constraints.add(NotConstraint.not(AllConstraint.all(subList)));
    }

    private void handleTerm(String term, List<Constraint> constraints) {
        if (term.startsWith("\"") && term.endsWith("\"")) {
            constraints.add(ContainsConstraint.contains(term.substring(1, term.length() - 1),
                    false, false, path));
            return;
        }

        List<Constraint> result = new ArrayList<>();
        String[] parts = splitOnInnerAsterisk.split(term);
        for (int i = 0; i < parts.length; i++) {
            boolean prefixSearch = i != 0;
            boolean postfixSearch = i != parts.length - 1;
            String part = parts[i];
            if (part.endsWith("*")) {
                part = part.substring(0, part.length() - 1);
                postfixSearch = true;
            }
            if (part.startsWith("*")) {
                part = part.substring(1);
                prefixSearch = true;
            }
            result.add(ContainsConstraint.contains(part, prefixSearch, postfixSearch, path));
        }
        constraints.add(all(result));
    }

    private List<String> splitInTermsAndOperators(String query) {
        List<String> parts = new ArrayList<>();

        Matcher matcher = termPattern.matcher(query.trim());
        while (matcher.find()) {
            String group = matcher.group().trim();
            if (!group.isEmpty() && !group.equals("\"") && !group.equals("AND") && !group.equals("&")) {
                parts.add(group.equals("OR") ? "|" : group);
            }
        }
        return parts;
    }
}