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

import io.fluxcapacitor.common.search.Document;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.search.Document.EntryType.NUMERIC;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Value
public class DocumentStats {

    public static List<DocumentStats> compute(Stream<Document> documents, List<String> fields, List<String> groupBy) {
        var finalFields = fields.isEmpty() ? List.of("") : fields;
        Map<List<String>, List<Document>> groups = documents.collect(
                groupingBy(d -> groupBy.stream().map(
                        g -> d.getEntryAtPath(g).map(Document.Entry::getValue).orElse(null)).collect(toList())));
        Stream<DocumentStats> statsStream = groups.entrySet().stream().map(e -> new DocumentStats(
                finalFields.stream().collect(toMap(identity(), f -> new FieldStats(e.getValue(), f), (a, b) -> b)),
                asGroup(groupBy, e.getKey())));
        Comparator<DocumentStats> comparator = groupBy.stream().map(g -> Comparator.<DocumentStats>nullsLast(
                        comparing(d -> d.getGroup().get(g), Comparator.nullsLast(naturalOrder()))))
                .reduce(Comparator::thenComparing).orElse((a, b) -> 0);
        return statsStream.sorted(comparator).collect(toList());
    }

    private static Group asGroup(List<String> groupBy, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < groupBy.size(); i++) {
            result.put(groupBy.get(i), values.get(i));
        }
        return new Group(result);
    }

    Map<String, FieldStats> fieldStats;
    Group group;

    @Value
    @AllArgsConstructor
    public static class FieldStats {
        long count;
        BigDecimal min;
        BigDecimal max;
        BigDecimal sum;
        BigDecimal average;

        protected FieldStats(List<Document> documents, String path) {
            this.count = documents.size();
            List<BigDecimal> values = path.isBlank() ? List.of()
                    : documents.stream().flatMap(d -> d.getEntryAtPath(path).stream())
                    .filter(e -> e.getType() == NUMERIC).map(e -> new BigDecimal(e.getValue())).sorted()
                    .toList();
            if (values.isEmpty()) {
                min = max = sum = average = null;
            } else {
                min = values.get(0);
                max = values.get(values.size() - 1);
                sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                average = sum.divide(new BigDecimal(values.size()), new MathContext(10, RoundingMode.HALF_UP))
                        .stripTrailingZeros();
            }
        }
    }
}
