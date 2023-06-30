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

package io.fluxcapacitor.common.api.tracking;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.beans.ConstructorProperties;

@Value
@EqualsAndHashCode(callSuper = true)
public class ClaimSegment extends Read {
    @ConstructorProperties({"consumer", "trackerId", "maxTimeout", "clientControlledIndex", "typeFilter", "filterMessageTarget", "lastIndex", "purgeTimeout"})
    public ClaimSegment(String consumer, String trackerId, long maxTimeout, boolean clientControlledIndex, String typeFilter,
                        boolean filterMessageTarget, Long lastIndex, Long purgeTimeout) {
        super(consumer, trackerId, 0, maxTimeout, typeFilter, filterMessageTarget, false,
              false, clientControlledIndex, lastIndex, purgeTimeout);
    }

    public ClaimSegment(Read read) {
        this(read.getConsumer(), read.getTrackerId(), read.getMaxTimeout(), read.isClientControlledIndex(),
             read.getTypeFilter(), read.isFilterMessageTarget(), read.getLastIndex(), read.getPurgeTimeout());
    }
}
