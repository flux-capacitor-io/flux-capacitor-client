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

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public class IndexDocuments extends Command {
    List<SerializedDocument> documents;
    boolean ifNotExists;
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
    public Metric toMetric() {
        return new Metric(getSize(), ifNotExists, guarantee,
                documents.stream().map(SerializedDocument::getId).collect(Collectors.toList()));
    }

    @Override
    public String routingKey() {
        return documents.get(0).getId();
    }

    @Value
    public static class Metric {
        int size;
        boolean ifNotExists;
        Guarantee guarantee;
        List<String> ids;
    }
}
