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

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Value
public class IndexDocuments extends Request {
    List<SerializedDocument> documents;
    Guarantee guarantee;

    @JsonIgnore
    public int getSize() {
        return documents.size();
    }

    @Override
    public String toString() {
        return "IndexDocuments of length " + documents.size();
    }

    @Override
    public Object toMetric() {
        return null;
    }
}
