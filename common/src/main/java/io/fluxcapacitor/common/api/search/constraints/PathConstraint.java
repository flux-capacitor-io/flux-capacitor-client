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

public abstract class PathConstraint implements Constraint {

    @JsonAlias("path")
    public abstract List<String> getPaths();

    public abstract Constraint withPaths(List<String> paths);

    protected abstract boolean matches(Document.Entry entry);

    @Override
    public boolean matches(Document document) {
        return documentPredicate().test(document);
    }

    @Override
    public boolean hasPathConstraint() {
        return !getPaths().isEmpty();
    }

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
                        .anyMatch(e -> matches(e.getKey()) && (e.getValue().isEmpty() ? pathPredicate.test(null) :
                                e.getValue().stream().anyMatch(pathPredicate)));
    }
}
