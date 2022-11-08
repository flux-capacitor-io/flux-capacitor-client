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
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.List;

import static java.util.Optional.ofNullable;

@Value
public class Append extends Command {
    List<SerializedMessage> messages;
    Guarantee guarantee;

    @JsonIgnore
    public int getSize() {
        return messages.size();
    }

    public Guarantee getGuarantee() {
        return ofNullable(guarantee).orElse(Guarantee.NONE);
    }

    @Override
    public String toString() {
        return "Append of length " + messages.size();
    }

    @Override
    public Metric toMetric() {
        return new Metric(getSize(), getGuarantee());
    }

    @Value
    public static class Metric {
        int size;
        Guarantee guarantee;
    }
}
