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

package io.fluxcapacitor.common.api.search.constraints;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.SearchUtils.letterOrNumber;
import static io.fluxcapacitor.common.api.search.constraints.AllConstraint.all;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A {@link Constraint} that parses a human-friendly query string into a structured tree of constraints.
 * <p>
 * This class allows users to write intuitive search strings like:
 *
 * <pre>
 *   "error &amp; (critical | urgent) &amp; !resolved"
 *   "&quot;exact phrase&quot; | partial* &amp; !excluded"
 * </pre>
 * <p>
 * The query string supports:
 * <ul>
 *   <li><b>Terms</b>: space-separated words, optionally with wildcards (*).</li>
 *   <li><b>Exact phrases</b>: quoted strings (e.g., {@code "full text"}).</li>
 *   <li><b>Operators</b>: <code>&amp;</code> (AND), <code>|</code> (OR), <code>!</code> (NOT), and parentheses for grouping.</li>
 *   <li><b>Lookahead behavior</b>: Controlled via {@link #lookAheadForAllTerms} to mimic user-friendly search behavior.</li>
 * </ul>
 *
 * <p>
 * Wildcards:
 * <ul>
 *   <li>A trailing {@code *} enables prefix-matching (e.g., {@code hat*} matches {@code hatter}).</li>
 *   <li>A leading {@code *} enables postfix-matching (e.g., {@code *hat} matches {@code chat}).</li>
 *   <li>Both allows infix-matching (e.g., {@code *hat*} matches {@code groupchatting}).</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Match documents containing both "order" and "cancel"
 * QueryConstraint.query("order cancel");
 *
 * // Match documents with "error" or "failure"
 * QueryConstraint.query("error | failure");
 *
 * // Match documents where "payment" appears but not "test"
 * QueryConstraint.query("payment !test");
 *
 * // Match documents containing a phrase
 * QueryConstraint.query("\"order received\"");
 * }</pre>
 *
 * <p>
 * If a {@link #lookAheadForAllTerms} flag is set to {@code true}, then every term is treated as a look-ahead match
 * (e.g., {@code "chat"} will match "chatbot", "chatter", etc.). The query is applied over the configured document
 * paths, or across all paths if none are set.
 *
 * <p>
 * Internally, the query is decomposed into a structure of {@link ContainsConstraint}, {@link NotConstraint},
 * {@link AllConstraint}, and {@link AnyConstraint} nodes that evaluate against document fields.
 *
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *     <li>
 *         Queries using a <strong>prefix</strong> or <strong>postfix</strong> wildcard (e.g., {@code user*} or {@code *name}) are resolved at the data store level and typically fast.
 *     </li>
 *     <li>
 *         Queries using <strong>both prefix and postfix</strong> wildcards (e.g., {@code *log*}) are not natively supported by the backend and require <strong>in-memory filtering</strong>, which can be slow for large result sets.
 *     </li>
 *     <li>
 *         <strong>Similarly, prefix or postfix matches with terms shorter than 3 characters</strong> are also evaluated in-memory.
 *         <br>
 *         <em>Note:</em> This limitation does <strong>not</strong> apply to complete word matches (i.e., without wildcards).
 *         For example, {@code "to"} as an exact term is efficient, but {@code to*} or {@code *to} are not.
 *     </li>
 *     <li>
 *         When possible, prefer longer terms and structure queries to enable efficient execution via the underlying document store.
 *     </li>
 * </ul>
 *
 * @see ContainsConstraint
 * @see LookAheadConstraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryConstraint extends PathConstraint {
    private static final String operator = "&|()!";
    private static final Pattern termPattern =
            Pattern.compile(
                    String.format("\"[^\"]*\"|[%1$s]|[*%2$s][^\\s%1$s]+[*%2$s]|[*%2$s]+", operator, letterOrNumber),
                    Pattern.MULTILINE);
    private static final Pattern splitOnInnerAsterisk =
            Pattern.compile(String.format("(?<=[%1$s])\\*(?=[%1$s])", letterOrNumber));

    /**
     * Creates a {@link QueryConstraint} that parses and evaluates the given query string.
     *
     * @param query the human-readable query string
     * @param paths one or more document paths to apply the query to
     * @return a parsed and decomposed constraint
     */
    public static Constraint query(String query, String... paths) {
        return query(query, false, paths);
    }

    /**
     * Creates a {@link QueryConstraint} with optional lookahead behavior for terms.
     *
     * @param query                the query string
     * @param lookAheadForAllTerms if true, all terms will behave like lookaheads (i.e., include trailing wildcard
     *                             logic)
     * @param paths                the paths to apply the query to
     */
    public static Constraint query(String query, boolean lookAheadForAllTerms, String... paths) {
        return isBlank(query) ? NoOpConstraint.instance :
                new QueryConstraint(query, lookAheadForAllTerms, List.of(paths));
    }

    /**
     * The raw query string entered by the user.
     */
    @NonNull
    String query;

    /**
     * If true, all terms are treated as lookahead searches (i.e., match tokens that start with the term).
     */
    @With
    boolean lookAheadForAllTerms;

    /**
     * The document paths to which this query applies. If empty, the constraint is applied globally.
     */
    @With
    List<String> paths;

    /**
     * Overrides {@link Constraint#matches(Document)} to evaluate a decomposed constraint tree. The parsing is done
     * lazily and cached for reuse.
     */
    @Override
    public boolean matches(Document document) {
        return decompose().matches(document);
    }

    /**
     * Not supported directly on a single document entry. Throws {@link UnsupportedOperationException}.
     */
    @Override
    protected boolean matches(Document.Entry entry) {
        throw new UnsupportedOperationException();
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Constraint decompose = AllConstraint.all(createConstraints(splitInTermsAndOperators(getQuery())));

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
                                                        false, false, paths.toArray(String[]::new)));
            return;
        }

        List<Constraint> result = new ArrayList<>();
        String[] parts = splitOnInnerAsterisk.split(term);
        for (int i = 0; i < parts.length; i++) {
            boolean prefixSearch = i != 0;
            boolean postfixSearch = (i != parts.length - 1) || lookAheadForAllTerms;
            String part = parts[i];
            if (part.endsWith("*")) {
                part = part.substring(0, part.length() - 1);
                postfixSearch = true;
            }
            if (part.startsWith("*")) {
                part = part.substring(1);
                prefixSearch = true;
            }
            result.add(ContainsConstraint.contains(part, prefixSearch, postfixSearch, paths.toArray(String[]::new)));
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
