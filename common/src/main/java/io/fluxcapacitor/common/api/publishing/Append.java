/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.common.api.publishing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.List;

@Value
public class Append implements JsonType {
    List<SerializedMessage> messages;

    @JsonIgnore
    public int getSize() {
        return messages.size();
    }

    @Override
    public String toString() {
        return "Append of length " + messages.size();
    }

    @Override
    public Metric toMetric() {
        return new Metric(getSize());
    }

    @Value
    public static class Metric {
        int size;
    }
}
