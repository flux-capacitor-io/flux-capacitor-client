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
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.api.search.constraints.ContainsConstraint.letterOrNumber;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Value
public class LookAheadConstraint extends PathConstraint {
    static final Pattern termPattern =
            Pattern.compile(String.format("\"[^\"]*\"|[%1$s][^\\s]*[%1$s]|[%1$s]", letterOrNumber), Pattern.MULTILINE);

    public static Constraint lookAhead(String lookAhead, String... paths) {
        if (isBlank(lookAhead)) return noOp;
        switch (paths.length) {
            case 0:
                return new LookAheadConstraint(lookAhead, null);
            case 1:
                return new LookAheadConstraint(lookAhead, paths[0]);
            default:
                return new AnyConstraint(Arrays.stream(paths).map(p -> new LookAheadConstraint(lookAhead, p)).collect(
                        Collectors.toList()));
        }
    }

    @NonNull String lookAhead;
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
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = AllConstraint.all(createConstraints(splitInTerms(lookAhead)));

    private List<Constraint> createConstraints(List<String> parts) {
        return parts.stream().filter(StringUtils::isNotBlank)
                .map(part -> ContainsConstraint.contains(part, false, true, path))
                .collect(Collectors.toList());
    }

    private List<String> splitInTerms(String query) {
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
