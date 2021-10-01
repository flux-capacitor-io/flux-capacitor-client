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

package io.fluxcapacitor.javaclient.givenwhenthen;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.*;
import static io.fluxcapacitor.common.api.search.constraints.ExistsConstraint.exists;
import static io.fluxcapacitor.common.api.search.constraints.MatchConstraint.match;
import static io.fluxcapacitor.common.api.search.constraints.NotConstraint.not;
import static io.fluxcapacitor.common.api.search.constraints.QueryConstraint.query;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GivenWhenThenSearchTest {

    @Test
    void testPhraseMatching() {
        expectMatch(query("see what"));
        expectMatch(query("see what", "foo"));
        expectMatch(query("see wh*"));
        expectMatch(query("se* wh*"));
        expectMatch(query("fin* wh*"));
        expectMatch(query("* what"));
        expectMatch(query("*e what", "foo"));
        expectNoMatch(query("*a what", "foo"));
        expectNoMatch(query("bla bla"));
        expectNoMatch(query("see what", "wrongField"));
    }

    @Test
    void testSymbols() {
        expectNoMatch(query("see wh\\*", "symbols"));

        expectMatch(query("or front*", "symbols"));
        expectMatch(query("or front  *", "symbols"));
        expectMatch(query("or \\front*", "symbols"));
        expectMatch(query("or \\*", "symbols"));
        expectMatch(query("or xml*", "symbols"));
        expectMatch(query("or <xml>*", "symbols"));

        expectMatch(query("or mid\\dle*", "symbols"));
        expectMatch(query("or mid\\*", "symbols"));
        expectMatch(query("or mid\\*", "symbols"));
        expectNoMatch(query("or middle*", "symbols"));
        expectNoMatch(query("or mid-dle*", "symbols"));
        expectNoMatch(query("or mid-*", "symbols"));
        expectMatch(query("or (mid)*", "symbols")); //operators are treated differently

        expectMatch(query("Anne*", "symbols"));
        expectMatch(query("Anne-*", "symbols"));
        expectMatch(query("Anne-gre*", "symbols"));
    }

    @Test
    void testWeirdChars() {
        expectMatch(query("ẏṏṳṙ ẇḕḭṙḊ ṮḕẌ*", "weirdChars"));
        expectNoMatch(query("ẏṏṳṙ ẇḕḭṙḊo ṮḕẌ*", "weirdChars"));
        expectMatch(query("ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹ*", "weirdChars"));
        expectNoMatch(query("XXÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬ*", "weirdChars"));
        expectMatch(match("ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ", "weirdChars"));
        expectNoMatch(match("ẏṏṳṙ ẇḕḭṙḊo ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ", "weirdChars"));
    }

    @Test
    void testFieldMatching() {
        expectMatch(match(SomeDocument.ID, "someId"));
        expectMatch(match(SomeDocument.ID));
        expectMatch(match(SomeDocument.ID, "someOtherField", "someId"));
        expectMatch(match("Let's see what we can find", "foo"));
        expectNoMatch(match(SomeDocument.ID.toLowerCase(), "someId"));
        expectNoMatch(match(SomeDocument.ID, "wrongField"));
    }

    @Test
    void testNumberMatching() {
        //at least
        expectMatch(atLeast(18.5, "someNumber"));
        expectMatch(atLeast(20.5, "someNumber"));
        expectMatch(atLeast(20.50, "someNumber"));
        expectNoMatch(atLeast(18.5, "wrongField"));
        expectNoMatch(atLeast(21, "someNumber"));

        //below
        expectMatch(below(21.5, "someNumber"));
        expectNoMatch(below(20, "someNumber"));
        expectNoMatch(below(20.5, "someNumber"));

        //between
        expectMatch(between(20, 30, "someNumber"));
        expectMatch(between(20.5, 30, "someNumber"));
        expectNoMatch(between(21, 30, "someNumber"));
        expectNoMatch(between(20, 20.5, "someNumber"));

        assertThrows(Throwable.class, () -> expectNoMatch(atLeast(18.5, null)), "Path should be required");
    }

    @Test
    void testExistsConstraint() {
        expectMatch(exists("someId"));
    }

    @Test
    void testCombineConstraints() {
        expectMatch(query("see wh*"), below(21.5, "someNumber"));
        expectNoMatch(not(query("see wh*").and(below(21.5, "someNumber"))));
        expectNoMatch(query("see wh*", "wrongField"), below(21.5, "someNumber"));
        expectMatch(not(query("see wh*", "wrongField").and(below(21.5, "someNumber"))));
        expectMatch(any(query("see wh*", "wrongField").or(below(21.5, "someNumber"))));
        expectNoMatch(not(exists("someId")));
    }

    @Test
    void testPathMatching() {
        expectMatch(match(true, "booleans/first"));
        expectMatch(match(true, "booleans/second"));
        expectMatch(match(true, "*/second"));
        expectMatch(match(true, "booleans/*"));
        expectMatch(match(true, "booleans/**"));
        expectMatch(match(true, "**"));
        expectNoMatch(match(false, "**"));
        expectNoMatch(match(false, "booleans/first"));
        expectMatch(match(true, "booleans/third/inner"));
        expectMatch(match(true, "booleans/*/inner"));
        expectMatch(match(true, "booleans/**/inner"));
        expectMatch(match(true, "**/inner"));
    }

    @Test
    void testExpectedDocuments() {
        SomeDocument someDocument = new SomeDocument();
        TestFixture.create().when(fc -> {
            fc.documentStore().index(someDocument, "test", "test");
            fc.documentStore().index("bla", "test2", "test");
        }).expectDocuments(singletonList("bla"));
    }

    @Test
    void testOnlyExpectedDocuments() {
        SomeDocument someDocument = new SomeDocument();
        TestFixture.create().when(fc -> fc.documentStore().index(someDocument, "test", "test"))
                .expectOnlyDocuments(singletonList(someDocument));
    }

    @Test
    void testNoExpectedDocumentsLike() {
        SomeDocument someDocument = new SomeDocument();
        TestFixture.create().when(fc -> {
            fc.documentStore().index(someDocument, "test", "test");
            fc.documentStore().index("bla", "test2", "test");
        }).expectNoDocumentsLike(singletonList("bla2"));
    }

    @Test
    void testSearchInArray() {
        expectMatch(query("10", "mapList/key1"));
        expectMatch(query("10", "mapList/0/key1"));
        expectNoMatch(query("10", "mapList/1/key1"));
        expectMatch(query("value2", "mapList/key2"));
        expectMatch(query("value2", "mapList/1/key2"));
    }

    @Test
    void testLongNumber() {
        expectMatch(query("106193501828612100"));
    }

    @Test
    void testMetricsMessageExample() {
        JsonNode jsonNode = JsonUtils.fromFile(getClass(), "metrics-message.json", JsonNode.class);
        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", query("106193501828612100", "messageIndex"))
                .<List<JsonNode>>expectResult(r -> !r.isEmpty() && r.get(0).get("payload") != null);
    }

    @Test
    void testExcludePaths() {
        JsonNode jsonNode = JsonUtils.fromFile(getClass(), "metrics-message.json", JsonNode.class);
        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.exclude("payload"))
                .<List<JsonNode>>expectResult(r -> !r.isEmpty() && r.get(0).get("payload") == null
                        && r.get(0).get("segment") != null
                        && r.get(0).get("metadata") != null
                        && r.get(0).get("metadata").get("requestId") != null);

        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.exclude("segment"))
                .<List<JsonNode>>expectResult(r -> r.get(0).get("segment") == null);

        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.exclude("metadata/requestId"))
                .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") != null
                        && r.get(0).get("metadata").get("$consumer") != null
                        && r.get(0).get("metadata").get("requestId") == null);

        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.exclude("metadata/**"))
                .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") == null);
    }

    @Test
    void testIncludePaths() {
        JsonNode jsonNode = JsonUtils.fromFile(getClass(), "metrics-message.json", JsonNode.class);
        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.includeOnly("payload"))
                .<List<JsonNode>>expectResult(r -> !r.isEmpty()
                        && r.get(0).get("payload") != null && r.get(0).get("payload").get("requestId") != null
                        && r.get(0).get("segment") == null
                        && r.get(0).get("metadata") == null);

        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", search -> search.includeOnly("payload/requestId"))
                .<List<JsonNode>>expectResult(r -> !r.isEmpty()
                        && r.get(0).get("payload") != null && r.get(0).get("payload").get("requestId") != null
                        && r.get(0).get("payload").get("strategy") == null
                        && r.get(0).get("segment") == null
                        && r.get(0).get("metadata") == null);
    }

    @Test
    void testBulkUpdateSerialization() {
        JacksonSerializer serializer = new JacksonSerializer();
        var object = MockObjectWithBulkUpdates.builder()
                .update(new DeleteDocument("id", "test"))
                .update(IndexDocument.builder().id("id2").collection("test").timestamp(Instant.now())
                                .object(new SomeDocument().toBuilder().mapList(emptyList()).build()).build()).build();
        MockObjectWithBulkUpdates serialized = serializer.deserialize(serializer.serialize(object));
        assertEquals(object, serialized);
    }

    @Test
    void testGetById() {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocument(document, "testId", "test")
                .whenApplying(fc -> fc.documentStore().fetchDocument("testId", "test").orElse(null))
                .expectResult(document);
    }

    @Value
    @Builder
    private static class MockObjectWithBulkUpdates {
        @Singular
        List<BulkUpdate> updates;
    }

    private void expectMatch(Constraint... constraints) {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocuments("test", document).whenSearching("test", constraints)
                .expectResult(singletonList(document));
    }

    private void expectNoMatch(Constraint... constraints) {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocuments("test", document).whenSearching("test", constraints)
                .expectResult(emptyList());
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    private static class SomeDocument {
        private static final String ID = "123A45B67c";

        String someId;
        BigDecimal longNumber;
        String foo;
        BigDecimal someNumber;
        Map<String, Object> booleans;
        List<Map<String, Object>> mapList;
        String symbols, weirdChars;

        public SomeDocument() {
            this.someId = ID;
            this.longNumber = new BigDecimal("106193501828612100");
            this.foo = "Let's see what we can find";
            this.someNumber = new BigDecimal("20.5");
            this.booleans = Stream.of("first", "second", "third", "third").collect(
                    toMap(identity(), s -> true, (a, b) -> singletonMap("inner", true), LinkedHashMap::new));
            this.mapList = Arrays.asList(singletonMap(
                    "key1", new BigDecimal(10)), singletonMap("key2", "value2"));
            this.symbols = "Can you find slash in mid\\dle or \\front, or find <xml>? Anne-gre";
            this.weirdChars = "ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ";
        }
    }
}
