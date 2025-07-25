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

package io.fluxcapacitor.javaclient.search;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.Group;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;
import io.fluxcapacitor.common.api.search.constraints.FacetConstraint;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.search.Facet;
import io.fluxcapacitor.common.search.Sortable;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.casting.Upcast;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import io.fluxcapacitor.javaclient.test.Given;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.When;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.Guarantee.STORED;
import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.atLeast;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.below;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.between;
import static io.fluxcapacitor.common.api.search.constraints.ContainsConstraint.contains;
import static io.fluxcapacitor.common.api.search.constraints.ExistsConstraint.exists;
import static io.fluxcapacitor.common.api.search.constraints.LookAheadConstraint.lookAhead;
import static io.fluxcapacitor.common.api.search.constraints.MatchConstraint.match;
import static io.fluxcapacitor.common.api.search.constraints.NotConstraint.not;
import static io.fluxcapacitor.common.api.search.constraints.QueryConstraint.query;
import static io.fluxcapacitor.javaclient.FluxCapacitor.prepareIndex;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchTest {

    @Test
    void storeSearchable() {
        TestFixture.create().givenDocument(new SomeSearchable("foo", Instant.now()))
                .whenSearching(SomeSearchable.class)
                .expectResult(r -> r.size() == 1)
                .mapResult(List::getFirst)
                .expectResult(s -> Objects.equals(s.getId(), "foo"));
    }

    @Test
    void storeViaIndexOperation() {
        TestFixture.create().given(fc -> prepareIndex(new SomeSearchable("foo", Instant.now()))
                        .addMetadata("metafoo", "metabar").indexAndWait())
                .whenSearching(SomeSearchable.class, s -> s.matchMetadata("metafoo", "metabar"))
                .expectResult(r -> r.size() == 1)
                .mapResult(List::getFirst)
                .expectResult(s -> Objects.equals(s.getId(), "foo"));
    }

    @Test
    void storeRandomBytes() {
        TestFixture.create().given(fc -> {
                    var document = new SerializedDocument("test", null, null, "uploads",
                                                          new Data<>("foobar".getBytes(), "whatever", 0, "text/plain"), "",
                                                          emptySet(), emptySet());
                    FluxCapacitor.get().client().getSearchClient().index(List.of(document), Guarantee.STORED, false).get();
                }).whenApplying(fc -> FluxCapacitor.search("emptyCollection").fetchAll())
                .expectResult(List::isEmpty)
                .andThen()
                .whenApplying(
                        fc -> FluxCapacitor.get().client().getSearchClient().search(SearchDocuments.builder().query(
                                SearchQuery.builder().collections(List.of("uploads")).build()).build(), 100)
                                .map(SearchHit::getValue).toList())
                .expectResult(r -> r.size() == 1
                                   && "foobar".equals(new String(r.getFirst().getDocument().getValue())));
    }

    @Value
    @Searchable(timestampPath = "timestamp")
    static class SomeSearchable {
        String id;
        Instant timestamp;
    }

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

        expectMatch(lookAhead("see what"));
        expectMatch(lookAhead("se wha"));
        expectNoMatch(lookAhead("ee hat"));
        expectMatch(lookAhead("se wha", "foo", "wrongField"));
        expectMatch(lookAhead("what slash", "foo", "symbols"));
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
        expectMatch(
                query("ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹ*",
                      "weirdChars"));
        expectNoMatch(
                query("XXÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬ*",
                      "weirdChars"));
        expectMatch(
                match("ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ",
                      true, "weirdChars"));
        expectMatch(
                match("ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ".toLowerCase(),
                      "weirdChars"));
        expectNoMatch(
                match("ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ".toLowerCase(),
                      true, "weirdChars"));
        expectNoMatch(
                match("ẏṏṳṙ ẇḕḭṙḊo ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ",
                      "weirdChars"));
    }

    @Test
    void testFieldMatching() {
        expectMatch(match(SomeDocument.ID, "someId"));
        expectMatch(match(SomeDocument.ID));
        expectMatch(match(SomeDocument.ID, "someOtherField", "someId"));
        expectMatch(match("Let's see what we can find", "foo"));
        expectMatch(match(SomeDocument.ID.toLowerCase(), "someId"));
        expectNoMatch(match(SomeDocument.ID.toLowerCase(), true, "someId"));
        expectNoMatch(match(SomeDocument.ID, "wrongField"));
    }

    @Test
    void testBetween() {
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
    void betweenTimestamps() {
        expectMatch(between(Instant.parse("2023-06-14T22:00:00Z"),
                            Instant.parse("2023-06-16T22:00:00Z"), "status/sent/date"));
    }

    @Test
    void testExistsConstraint() {
        expectMatch(exists("someId"));
        expectNoMatch(exists("some"));
        expectMatch(exists("booleans"));
        expectMatch(exists("booleans/first"));
    }

    @Test
    void testCombineConstraints() {
        expectMatch(query("see wh*"), below(21.5, "someNumber"));
        expectNoMatch(not(query("see wh*").and(below(21.5, "someNumber"))));
        expectNoMatch(query("see wh*", "wrongField"), below(21.5, "someNumber"));
        expectMatch(not(query("see wh*", "wrongField").and(below(21.5, "someNumber"))));
        expectMatch(any(query("see wh*", "wrongField").or(below(21.5, "someNumber"))));
        expectNoMatch(not(exists("someId")));
        expectMatch(query("see wh* | someId"));
        expectMatch(query("(see & wh*) | someId"));
        expectNoMatch(query("see wh* & someId"));
        expectMatch(query("see wh* & slash"));
        expectMatch(query("see wh & slash", true));
        expectNoMatch(query("see wh & slash", false));
        expectNoMatch(query("\"slas\"", true));
        expectNoMatch(query("\"slas\"", false));
        expectNoMatch(query("see wh & slashx", true));
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
        TestFixture.create().givenDocuments("test", "metrics-message.json")
                .<JsonNode>whenSearching("test", query("106193501828612100", "messageIndex"))
                .expectResult(r -> !r.isEmpty() && r.getFirst().get("payload") != null);
    }

    @Test
    void testDeserializingKnownType() {
        TestFixture testFixture = TestFixture.create().registerCasters(new ReadUpcaster());
        var read = JsonUtils.fromFile("read-payload.json", Read.class);
        var document = testFixture.getFluxCapacitor().documentStore().getSerializer().toDocument(
                read, "foo", "test", null, null);
        testFixture.given(fc -> fc.client().getSearchClient().index(List.of(document), STORED, false).get())
                .whenApplying(fc -> FluxCapacitor.search("test").fetchAll())
                .expectResult(r -> r.size() == 1)
                .mapResult(r -> (Read) r.getFirst())
                .expectResult(r -> r.getTrackerId().equals(read.getTrackerId()) && r.getRequestId() == 12345);
    }

    static class ReadUpcaster {
        @Upcast(type = "io.fluxcapacitor.common.api.tracking.Read", revision = 0)
        ObjectNode upcast(ObjectNode read) {
            return read.set("requestId", IntNode.valueOf(12345));
        }
    }

    @Test
    void testDeserializingUnknownType() {
        TestFixture testFixture = TestFixture.create();
        var read = JsonUtils.fromFile("read-payload.json", JsonNode.class);
        var document = testFixture.getFluxCapacitor().documentStore().getSerializer().toDocument(
                read, "foo", "test", null, null);
        var documentWithUnknownType = document.withData(() -> document.getDocument().withType("unknown"));
        testFixture.given(
                        fc -> fc.client().getSearchClient().index(List.of(documentWithUnknownType), STORED, false).get())
                .whenApplying(fc -> FluxCapacitor.search("test").fetchAll(Read.class))
                .expectResult(r -> !r.isEmpty());
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
    void testGroupSerialization() {
        Group input = Group.of("foo", "bar", "flux", "capacitor");
        String json = JsonUtils.asJson(input);
        var output = JsonUtils.fromJson(json, Group.class);
        assertEquals(input, output);
    }

    @Test
    void testGetById() {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocument(document, "testId", "test")
                .whenApplying(fc -> fc.documentStore().fetchDocument("testId", "test").orElse(null))
                .expectResult(document);
    }

    @Nested
    class PathTests {
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
        void testSearchUsingDots() {
            expectMatch(match(true, "booleans.first"));
            expectNoMatch(match(false, "booleans.first"));
            expectMatch(query("10", "mapList.0.key1"));
            expectMatch(match(true, "booleans.fourth/key"));
        }

        @Test
        void testPathEscaping() {
            expectMatch(match(true, "booleans/fourth/key"));
            expectMatch(match(true, "booleans/5"));
            expectMatch(match(true, "booleans/6\\.1"));
        }
    }

    @Nested
    class IncludesAndExcludesTests {
        final JsonNode jsonNode = JsonUtils.fromFile("metrics-message.json", JsonNode.class);

        @Test
        void testExcludePaths() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("payload"))
                    .expectResult(r -> !r.isEmpty() && r.getFirst().get("payload") == null
                                       && r.getFirst().get("segment") != null
                                       && r.getFirst().get("metadata") != null
                                       && r.getFirst().get("metadata").get("requestId") != null);

            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("segment"))
                    .expectResult(r -> r.getFirst().get("segment") == null);

            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("metadata/requestId"))
                    .expectResult(r -> r.getFirst().get("metadata") != null
                                       && r.getFirst().get("metadata").get("$consumer") != null
                                       && r.getFirst().get("metadata").get("requestId") == null);

            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("metadata/**"))
                    .expectResult(r -> r.getFirst().get("metadata") == null);
        }

        @Test
        void testIncludePaths() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.includeOnly("payload"))
                    .expectResult(r -> !r.isEmpty()
                                       && r.getFirst().get("payload") != null
                                       && r.getFirst().get("payload").get("requestId") != null
                                       && r.getFirst().get("segment") == null
                                       && r.getFirst().get("metadata") == null);

            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.includeOnly("payload/requestId"))
                    .expectResult(r -> !r.isEmpty()
                                       && r.getFirst().get("payload") != null
                                       && r.getFirst().get("payload").get("requestId") != null
                                       && r.getFirst().get("payload").get("strategy") == null
                                       && r.getFirst().get("segment") == null
                                       && r.getFirst().get("metadata") == null);
        }

        @Test
        void testExcludeMultiLevelArray() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("payload/array/anotherArray"))
                    .expectResult(r -> r.getFirst().get("metadata") != null
                                       && r.getFirst().get("payload").get("array") != null
                                       && r.getFirst().get("payload").get("array").get(0).get("anotherArray")
                                          == null);
        }

        @Test
        void testMultipleExcludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.exclude("payload/array/anotherArray",
                                                                              "payload/requestId"))
                    .expectResult(r -> r.getFirst().get("metadata") != null
                                       && r.getFirst().get("payload").get("array") != null
                                       &&
                                       r.getFirst().get("payload").get("array").get(0).get("anotherArray")
                                       == null);
        }

        @Test
        void testMultipleIncludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test",
                                             search -> search.includeOnly("payload/array/anotherArray",
                                                                          "payload/requestId"))
                    .expectResult(r -> r.getFirst().get("metadata") == null
                                       && r.getFirst().get("payload").get("array") != null
                                       &&
                                       r.getFirst().get("payload").get("array").get(0).get("anotherArray")
                                       != null
                                       && r.getFirst().get("payload").get("requestId") != null);
        }

        @Test
        void testIncludesAndExcludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .<JsonNode>whenSearching("test", search -> search.includeOnly("payload/array", "payload/requestId")
                            .exclude("payload/array/anotherArray"))
                    .expectResult(r -> r.getFirst().get("metadata") == null
                                       && r.getFirst().get("payload").get("array") != null
                                       &&
                                       r.getFirst().get("payload").get("array").get(0).get("anotherArray")
                                       == null
                                       && r.getFirst().get("payload").get("requestId") != null);
        }
    }

    @Nested
    class TimeConstraintTests {
        Instant documentStart = Instant.parse("2021-12-01T12:00:00Z");
        Instant documentEnd = Instant.parse("2021-12-30T12:00:00Z");
        When testFixture =
                TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", documentStart, documentEnd);

        @Test
        void searchSinceBeforeDocument() {
            testFixture.<JsonNode>whenSearching("foobar", s -> s.since(documentStart.minusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchBeforeAfterDocument() {
            testFixture.<JsonNode>whenSearching("foobar", s -> s.before(documentEnd.plusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodBeforeAndAfterDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentStart.minusSeconds(1),
                                                                documentEnd.plusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodStartsBeforeDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentStart.minusSeconds(1),
                                                                documentEnd.minusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodEndsAfterDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentStart.plusSeconds(1),
                                                                documentEnd.plusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodFallsWithinThatOfDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentStart.plusSeconds(1),
                                                                documentEnd.minusSeconds(1))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodBeforeThatOfDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentStart.minusSeconds(5),
                                                                documentStart.minusSeconds(1))
            ).expectResult(List::isEmpty);
        }

        @Test
        void searchPeriodAfterThatOfDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentEnd.plusSeconds(1), documentEnd.plusSeconds(5))
            ).expectResult(List::isEmpty);
        }

        @Test
        void searchPeriodStartsAtEndOfDocument() {
            testFixture.<JsonNode>whenSearching("foobar",
                                                s -> s.inPeriod(documentEnd, documentEnd.plusSeconds(5))
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodEndsAtStartOfDocument() {
            testFixture.<JsonNode>whenSearching("foobar", s -> s.before(documentStart)
            ).expectResult(List::isEmpty);
        }

        @Test
        void searchPeriodEndsAtStartOfDocument_inclusive() {
            testFixture.<JsonNode>whenSearching("foobar", s -> s.before(documentStart, true)
            ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void noDocumentEndTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", documentStart, null)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1),
                                                             documentStart.plusSeconds(1))
                    ).expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", documentStart, null)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1), documentStart)
                    ).expectResult(List::isEmpty);
        }

        @Test
        void noDocumentStartTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, documentEnd)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, documentEnd)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).expectResult(docs -> docs.size() == 1);
        }

        @Test
        void noDocumentStartAndEndTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, null)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, null)
                    .<JsonNode>whenSearching("foobar",
                                             s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).expectResult(l -> !l.isEmpty());
        }
    }

    @Nested
    class SortingTests {

        private final Instant now = Instant.now();

        private final SomeDocument a = new SomeDocument().toBuilder().someId("a").symbols("aaa")
                .someNumber(new BigDecimal("50")).ts(Instant.parse("2023-12-01T12:00:00Z")).build();
        private final SomeDocument b = new SomeDocument().toBuilder().someId("b").build();

        private final Given testFixture = TestFixture.create().atFixedTime(now)
                .givenDocument(a, "id1", "test", now)
                .givenDocument(b, "id2", "test", now.plusSeconds(1));

        @Test
        void sortTimeDescending() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test").streamHits().toList())
                    .expectResult(results -> results.size() == 2)
                    .expectResult(results -> "id2".equals(results.getFirst().getId())
                                             && "id1".equals(results.get(1).getId()));
        }

        @Test
        void sortTimeAscending() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test")
                                    .sortByTimestamp(false).streamHits().toList())
                    .expectResult(results -> results.size() == 2)
                    .expectResult(results -> "id1".equals(results.getFirst().getId())
                                             && "id2".equals(results.get(1).getId()));
        }

        @Test
        void sortOnField() {
            SomeDocument nullFields = new SomeDocument().toBuilder().someId("nullFields").symbols(null).someNumber(null).ts(null).build();
            testFixture
                    .givenDocument(nullFields, nullFields.getSomeId(), "test", now)
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("symbols").fetchAll())
                    .expectResult(List.of(a, b, nullFields))
                    .andThen()
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("someNumber").fetchAll())
                    .expectResult(List.of(b, a, nullFields))
                    .andThen()
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("ts").fetchAll())
                    .expectResult(List.of(a, b, nullFields));
        }

        @Test
        void sortOnFieldDesc() {
            SomeDocument nullFields = new SomeDocument().toBuilder().someId("nullFields").symbols(null).someNumber(null).ts(null).build();
            testFixture
                    .givenDocument(nullFields, nullFields.getSomeId(), "test", now)
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("symbols", true).fetchAll())
                    .expectResult(List.of(nullFields, b, a))
                    .andThen()
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("someNumber", true).fetchAll())
                    .expectResult(List.of(nullFields, a, b))
                    .andThen()
                    .whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("ts", true).fetchAll())
                    .expectResult(List.of(nullFields, b, a));
        }

        @Test
        void betweenWithNull() {
            SomeDocument nullFields = new SomeDocument().toBuilder().someId("nullFields").symbols(null).someNumber(null).ts(null).build();
            testFixture
                    .givenDocument(nullFields, nullFields.getSomeId(), "test", now)
                    .whenApplying(
                            fc -> fc.documentStore().search("test").constraint(
                                    BetweenConstraint.below(30, "someNumber")).sortBy("someNumber").fetchAll())
                    .expectResult(List.of(b))
                    .andThen()
                    .whenApplying(
                            fc -> fc.documentStore().search("test").constraint(
                                    BetweenConstraint.below(60, "someNumber")).sortBy("someNumber").fetchAll())
                    .expectResult(List.of(b, a));
        }

        @Test
        void sortOnMissingFieldHasNoEffect() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("missing", true).fetchAll())
                    .expectResult(List.of(a, b));
        }
    }

    @Nested
    class MultipleCollections {
        private final SomeDocument a = new SomeDocument().toBuilder().symbols("aaa").build();
        private final SomeDocument b = new SomeDocument();

        private final Given testFixture = TestFixture.create()
                .givenDocument(a, "id1", "c1")
                .givenDocument(b, "id2", "c2");

        @Test
        void searchListOfCollections() {
            testFixture.whenApplying(fc -> FluxCapacitor.search(List.of("c1", "c2")).fetchAll())
                    .expectResult(result -> result.contains(a) && result.contains(b));
        }

        @Test
        void searchViaSearchable() {
            @Searchable(collection = "c1")
            class Annotated {
            }
            testFixture.whenApplying(fc -> FluxCapacitor.search(List.of(Annotated.class, "c2")).fetchAll())
                    .expectResult(result -> result.contains(a) && result.contains(b));
        }

        @Test
        void classWithoutSearchable() {
            class NotAnnotated {
            }
            testFixture.givenDocument(a, "id1", NotAnnotated.class)
                    .whenApplying(fc -> FluxCapacitor.search(NotAnnotated.class).fetchAll()).expectResult(List.of(a));
        }

        @Test
        void toStringIfOther() {
            class C1 {
                @Override
                public String toString() {
                    return "c1";
                }
            }
            testFixture.whenApplying(fc -> FluxCapacitor.search(List.of(new C1(), "c2")).fetchAll())
                    .expectResult(result -> result.contains(a) && result.contains(b));
        }
    }

    @Nested
    class FacetSearch {
        private final Given testFixture = TestFixture.create()
                .givenDocuments("test", new SomeDocument(), new SomeDocument());

        @Test
        void facetMatch() {
            expectMatch(FacetConstraint.matchFacet("custom", "testCustom"));
            expectNoMatch(FacetConstraint.matchFacet("custom", "testCustom2"));
            expectNoMatch(FacetConstraint.matchFacet("custom", "TESTCustom"));
        }

        @Test
        void getFacetStats() {
            testFixture.whenApplying(fc -> FluxCapacitor.search("test").facetStats())
                    .<List<FacetStats>>expectResult(stats -> stats.size() == 6
                                                             && stats.contains(
                            new FacetStats("custom", "testCustom", 2))
                                                             && stats.contains(
                            new FacetStats("facetField", "testField", 2))
                                                             && stats.contains(
                            new FacetStats("facetGetter", "testGetter", 2))
                                                             && stats.contains(new FacetStats("facetList", "value1", 2))
                                                             && stats.contains(
                            new FacetStats("facetList/status", "value2", 2))
                                                             && stats.contains(
                            new FacetStats("selfAnnotated", "self", 2)));
        }
    }

    @Nested
    class UnstructuredDocSearchTests {
        private final Given testFixture = TestFixture.create()
                .givenDocuments("test", "some string");

        @Test
        void findInString() {
            testFixture.whenSearching("test", contains("some"))
                    .expectResult(list -> list.size() == 1 && "some string".equals(list.getFirst()));
        }

        @Test
        void noMatchIfPathIsAdded() {
            testFixture.whenSearching("test", contains("some", "id"))
                    .expectResult(List::isEmpty);
        }

        @Test
        void matchIfQueryPathIsEmpty() {
            testFixture.whenSearching("test", contains("some", ""))
                    .expectResult(list -> list.size() == 1);
        }
    }

    @Value
    @Builder
    private static class MockObjectWithBulkUpdates {
        @Singular
        List<BulkUpdate> updates;
    }

    private void expectMatch(Constraint... constraints) {
        SomeDocument document = new SomeDocument();
        TestFixture.create(
                        DefaultFluxCapacitor.builder().disableScheduledCommandHandler().disableAutomaticAggregateCaching())
                .givenDocuments("test", document).<JsonNode>whenSearching("test", constraints)
                .expectResult(singletonList(document));
    }

    private void expectNoMatch(Constraint... constraints) {
        SomeDocument document = new SomeDocument();
        TestFixture.create(
                        DefaultFluxCapacitor.builder().disableScheduledCommandHandler().disableAutomaticAggregateCaching())
                .givenDocuments("test", document).<JsonNode>whenSearching("test", constraints)
                .expectResult(emptyList());
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @Searchable(collection = "someDoc")
    public static class SomeDocument {
        private static final String ID = "123A45B67c";

        String someId;
        @Sortable
        BigDecimal longNumber;
        @Sortable
        Instant ts;
        String foo;
        @Sortable
        BigDecimal someNumber;
        Map<String, Object> booleans;
        List<Map<String, Object>> mapList;
        @Sortable
        String symbols;
        String weirdChars;
        Map<String, ?> status;
        @Facet
        String facetField;
        String facetGetter;
        @Facet
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        List<Object> facetList;
        @Facet
        SelfAnnotated selfAnnotated;

        public SomeDocument() {
            this.someId = ID;
            this.longNumber = new BigDecimal("106193501828612100");
            this.ts = Instant.parse("2024-12-01T12:00:00Z");
            this.foo = "Let's see what we can find";
            this.someNumber = new BigDecimal("20.5");
            this.booleans = Stream.of("first", "second", "third", "third", "fourth/key", "5", "6.1").collect(
                    toMap(identity(), s -> true, (a, b) -> singletonMap("inner", true), LinkedHashMap::new));
            this.mapList = Arrays.asList(singletonMap(
                    "key1", new BigDecimal(10)), singletonMap("key2", "value2"));
            this.symbols = "Can you find slash in mid\\dle or \\front, or find <xml>? Anne-gre";
            this.weirdChars =
                    "ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ";
            this.status = Map.of("sent", Map.of("date", "2023-06-15T22:00:00.180Z"));
            this.facetField = "testField";
            this.facetGetter = "testGetter";
            this.facetList = List.of("value1", new Nested("value2"));
            this.selfAnnotated = new SelfAnnotated("self");
        }

        @Facet
        public String getFacetGetter() {
            return facetGetter;
        }

        @Facet("custom")
        public String facetCustom() {
            return "testCustom";
        }

        @Value
        static class Nested {
            @Facet
            String status;
        }

        static class SelfAnnotated extends Id<Object> {
            protected SelfAnnotated(String id) {
                super(id, Object.class);
            }
        }
    }
}
