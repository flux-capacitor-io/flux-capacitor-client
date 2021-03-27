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

import io.fluxcapacitor.common.search.Document;
import lombok.NonNull;
import lombok.Value;

@Value
public class ExistsConstraint extends PathConstraint {
    public static ExistsConstraint exists(@NonNull String path) {
        return new ExistsConstraint(path);
    }

    @NonNull String path;

    @Override
    protected boolean matches(Document.Entry entry) {
        return entry.getType() != Document.EntryType.NULL;
    }
}
