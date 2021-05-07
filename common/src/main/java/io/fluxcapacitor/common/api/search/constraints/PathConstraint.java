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

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.common.search.Document.Path;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.function.Predicate;

public abstract class PathConstraint implements Constraint {

    public abstract String getPath();

    protected abstract boolean matches(Document.Entry entry);

    @Override
    public boolean matches(Document document) {
        return documentPredicate().test(document);
    }

    @Override
    public boolean hasPathConstraint() {
        return getPath() != null;
    }

    protected boolean checkPathBeforeEntry() {
        return false;
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    private final Predicate<Document> documentPredicate = computeDocumentPredicate();

    private Predicate<Document> computeDocumentPredicate() {
        Predicate<Path> pathPredicate = computePathPredicate();
        return checkPathBeforeEntry() ? d -> d.getEntries().entrySet().stream()
                .anyMatch(e -> e.getValue().stream().anyMatch(pathPredicate) && matches(e.getKey())) :
                d -> d.getEntries().entrySet().stream()
                        .anyMatch(e -> matches(e.getKey()) && e.getValue().stream().anyMatch(pathPredicate));
    }

    protected Predicate<Path> computePathPredicate() {
        if (getPath() == null) {
            return p -> true;
        }
        Predicate<String> predicate = SearchUtils.convertGlobToRegex(getPath()).asPredicate();
        return Arrays.stream(getPath().split("/")).anyMatch(SearchUtils::isInteger)
                ? p -> predicate.test(p.getValue()) : p -> predicate.test(p.getShortValue());
    }
}
