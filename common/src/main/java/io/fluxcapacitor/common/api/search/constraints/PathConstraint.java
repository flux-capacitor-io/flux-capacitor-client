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

import com.fasterxml.jackson.annotation.JsonAlias;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.common.search.Document.Path;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.function.Predicate;

/**
 * Abstract base class for constraints that apply to specific paths in a
 * {@link io.fluxcapacitor.common.search.Document}.
 * <p>
 * A {@code PathConstraint} allows filtering a document based on matching criteria applied to specific
 * {@link io.fluxcapacitor.common.search.Document.Entry} values at one or more paths. It forms the foundation for
 * constraints like {@link MatchConstraint}, {@link BetweenConstraint}, {@link ExistsConstraint}, etc.
 *
 * <h2>Path Filtering</h2>
 * The constraint applies only to values located at the paths specified via {@link #getPaths()}. If no paths are
 * specified, the constraint typically matches anywhere in the document.
 *
 * <h2>Performance consideration</h2>
 * By default, path filtering is applied <em>after</em> a document entry has passed the main match criteria. This order
 * can be changed by overriding {@link #checkPathBeforeEntry()}, which applies the path check first.
 *
 * @see Constraint
 * @see io.fluxcapacitor.common.search.Document
 * @see io.fluxcapacitor.common.search.Document.Entry
 * @see io.fluxcapacitor.common.search.Document.Path
 */
public abstract class PathConstraint implements Constraint {

    /**
     * Retrieves a list of paths associated with this constraint.
     *
     * @return a list of strings representing the paths
     */
    @JsonAlias("path")
    public abstract List<String> getPaths();

    public abstract Constraint withPaths(List<String> paths);

    /**
     * Evaluates whether the specified document entry satisfies the condition defined by this method's implementation.
     *
     * @param entry the document entry to evaluate
     * @return {@code true} if the entry satisfies the condition; {@code false} otherwise
     */
    protected abstract boolean matches(Document.Entry entry);

    @Override
    public boolean matches(Document document) {
        return documentPredicate().test(document);
    }

    @Override
    public boolean hasPathConstraint() {
        return !getPaths().isEmpty();
    }

    /**
     * Determines whether path filtering should be performed before evaluating entry-level match criteria.
     * <p>
     * By default, path filtering is applied after a document entry satisfies the primary match criteria (`false`
     * return). Subclasses can override this method to change the behavior so that path filtering is applied first
     * (`true` return).
     *
     * @return {@code true} if path filtering should be performed before evaluating entry-level criteria; {@code false}
     * if it should be applied after evaluating entry-level criteria.
     */
    protected boolean checkPathBeforeEntry() {
        return false;
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    private final Predicate<Document> documentPredicate = computeDocumentPredicate();

    private Predicate<Document> computeDocumentPredicate() {
        Predicate<Path> pathPredicate = getPaths().stream().map(
                Path::pathPredicate).reduce(Predicate::or).orElseGet(() -> p -> true);
        return checkPathBeforeEntry() ? d -> d.getEntries().entrySet().stream()
                .anyMatch(e -> e.getValue().stream().anyMatch(pathPredicate) && matches(e.getKey())) :
                d -> d.getEntries().entrySet().stream()
                        .anyMatch(e -> matches(e.getKey()) && (e.getValue().isEmpty()
                                ? pathPredicate.test(Path.EMPTY_PATH) :
                                e.getValue().stream().anyMatch(pathPredicate)));
    }
}
