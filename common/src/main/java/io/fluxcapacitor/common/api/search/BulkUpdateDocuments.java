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

import java.util.Collection;

@EqualsAndHashCode(callSuper = true)
@Value
public class BulkUpdateDocuments extends Command {
    Collection<DocumentUpdate> updates;
    Guarantee guarantee;

    @JsonIgnore
    public int getSize() {
        return updates.size();
    }

    @Override
    public String toString() {
        return "BulkUpdateDocuments of length " + updates.size();
    }

    @Override
    public Object toMetric() {
        return new Metric(updates.size(), guarantee);
    }

    @Override
    public String routingKey() {
        return updates.stream().map(DocumentUpdate::getId).findFirst().orElse(null);
    }

    @Value
    public static class Metric {
        int size;
        Guarantee guarantee;
    }
}
