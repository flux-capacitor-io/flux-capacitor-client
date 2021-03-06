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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Value
@Builder
@AllArgsConstructor
public class DocumentStats {
    Map<String, FieldStats> fieldStats;
    Map<String, String> group;

    @Value
    @Builder
    public static class FieldStats {
        long count;
        BigDecimal min;
        BigDecimal max;
        BigDecimal average;

        public static BigDecimal getAverage(List<@NonNull BigDecimal> bigDecimals) {
            BigDecimal sum = bigDecimals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(new BigDecimal(bigDecimals.size()), RoundingMode.HALF_UP);
        }
    }
}
