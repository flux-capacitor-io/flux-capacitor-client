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
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.beans.ConstructorProperties;
import java.util.Arrays;
import java.util.stream.Collectors;

import static java.lang.Character.isWhitespace;

@Value
public class FindConstraint extends PathConstraint {
    public static Constraint find(@NonNull String phrase, String... paths) {
        switch (paths.length) {
            case 0: return new FindConstraint(phrase, null);
            case 1: return new FindConstraint(phrase, paths[0]);
            default: return new AnyConstraint(Arrays.stream(paths).map(p -> new FindConstraint(phrase, p)).collect(
                    Collectors.toList()));
        }
    }

    String find;
    String path;
    boolean prefixSearch; //i.e. phrase was *foo

    @ConstructorProperties({"find", "path"})
    public FindConstraint(@NonNull String find, String path) {
        find = StringUtils.stripAccents(StringUtils.strip(find.toLowerCase()));
        if (find.endsWith("*") && !find.endsWith("\\*")) {
            find = find.substring(0, find.length() - 1);
        }
        if (find.startsWith("*")) {
            find = find.substring(1);
            prefixSearch = true;
        } else {
            prefixSearch = false;
        }
        this.find = find;
        this.path = path;
    }

    @Override
    protected boolean matches(Document.Entry entry) {
        String value = entry.asPhrase();
        int index = value.indexOf(find);
        return index >= 0 && (prefixSearch || index == 0 || isWhitespace(value.charAt(index - 1)));
    }
}
