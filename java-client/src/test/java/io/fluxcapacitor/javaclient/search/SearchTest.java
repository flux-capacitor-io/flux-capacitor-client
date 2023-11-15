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

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.Group;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.modeling.Searchable;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.atLeast;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.below;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.between;
import static io.fluxcapacitor.common.api.search.constraints.ExistsConstraint.exists;
import static io.fluxcapacitor.common.api.search.constraints.LookAheadConstraint.lookAhead;
import static io.fluxcapacitor.common.api.search.constraints.MatchConstraint.match;
import static io.fluxcapacitor.common.api.search.constraints.NotConstraint.not;
import static io.fluxcapacitor.common.api.search.constraints.QueryConstraint.query;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchTest {

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
        JsonNode jsonNode = JsonUtils.fromFile("metrics-message.json", JsonNode.class);
        TestFixture.create().givenDocuments("test", jsonNode)
                .whenSearching("test", query("106193501828612100", "messageIndex"))
                .<List<JsonNode>>expectResult(r -> !r.isEmpty() && r.get(0).get("payload") != null);
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
            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test", search -> search.includeOnly("payload"))
                    .<List<JsonNode>>expectResult(r -> !r.isEmpty()
                                                       && r.get(0).get("payload") != null
                                                       && r.get(0).get("payload").get("requestId") != null
                                                       && r.get(0).get("segment") == null
                                                       && r.get(0).get("metadata") == null);

            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test", search -> search.includeOnly("payload/requestId"))
                    .<List<JsonNode>>expectResult(r -> !r.isEmpty()
                                                       && r.get(0).get("payload") != null
                                                       && r.get(0).get("payload").get("requestId") != null
                                                       && r.get(0).get("payload").get("strategy") == null
                                                       && r.get(0).get("segment") == null
                                                       && r.get(0).get("metadata") == null);
        }

        @Test
        void testExcludeMultiLevelArray() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test", search -> search.exclude("payload/array/anotherArray"))
                    .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") != null
                                                       && r.get(0).get("payload").get("array") != null
                                                       &&
                                                       r.get(0).get("payload").get("array").get(0).get("anotherArray")
                                                       == null);
        }

        @Test
        void testMultipleExcludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test", search -> search.exclude("payload/array/anotherArray", "payload/requestId"))
                    .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") != null
                                                       && r.get(0).get("payload").get("array") != null
                                                       &&
                                                       r.get(0).get("payload").get("array").get(0).get("anotherArray")
                                                       == null);
        }

        @Test
        void testMultipleIncludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test",
                                   search -> search.includeOnly("payload/array/anotherArray", "payload/requestId"))
                    .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") == null
                                                       && r.get(0).get("payload").get("array") != null
                                                       &&
                                                       r.get(0).get("payload").get("array").get(0).get("anotherArray")
                                                       != null
                                                       && r.get(0).get("payload").get("requestId") != null);
        }

        @Test
        void testIncludesAndExcludes() {
            TestFixture.create().givenDocuments("test", jsonNode)
                    .whenSearching("test", search -> search.includeOnly("payload/array", "payload/requestId")
                            .exclude("payload/array/anotherArray"))
                    .<List<JsonNode>>expectResult(r -> r.get(0).get("metadata") == null
                                                       && r.get(0).get("payload").get("array") != null
                                                       &&
                                                       r.get(0).get("payload").get("array").get(0).get("anotherArray")
                                                       == null
                                                       && r.get(0).get("payload").get("requestId") != null);
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
            testFixture.whenSearching("foobar", s -> s.since(documentStart.minusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchBeforeAfterDocument() {
            testFixture.whenSearching("foobar", s -> s.before(documentEnd.plusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodBeforeAndAfterDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodStartsBeforeDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.minusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodEndsAfterDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.plusSeconds(1), documentEnd.plusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodFallsWithinThatOfDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.plusSeconds(1), documentEnd.minusSeconds(1))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodBeforeThatOfDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.minusSeconds(5), documentStart.minusSeconds(1))
            ).<List<?>>expectResult(List::isEmpty);
        }

        @Test
        void searchPeriodAfterThatOfDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentEnd.plusSeconds(1), documentEnd.plusSeconds(5))
            ).<List<?>>expectResult(List::isEmpty);
        }

        @Test
        void searchPeriodStartsAtEndOfDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentEnd, documentEnd.plusSeconds(5))
            ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void searchPeriodEndsAtStartOfDocument() {
            testFixture.whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.minusSeconds(1), documentStart)
            ).<List<?>>expectResult(List::isEmpty);
        }

        @Test
        void noDocumentEndTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", documentStart, null)
                    .whenSearching("foobar",
                                      s -> s.inPeriod(documentStart.minusSeconds(1), documentStart.plusSeconds(1))
                    ).<List<?>>expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", documentStart, null)
                    .whenSearching("foobar",
                                   s -> s.inPeriod(documentStart.minusSeconds(1), documentStart)
                    ).<List<?>>expectResult(List::isEmpty);
        }

        @Test
        void noDocumentStartTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, documentEnd)
                    .whenSearching("foobar",
                                   s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).<List<?>>expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, documentEnd)
                    .whenSearching("foobar",
                                   s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).<List<?>>expectResult(docs -> docs.size() == 1);
        }

        @Test
        void noDocumentStartAndEndTimestamp() {
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, null)
                    .whenSearching("foobar",
                                   s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).<List<?>>expectResult(docs -> docs.size() == 1);
            TestFixture.create().givenDocument(new SomeDocument(), "testId", "foobar", null, null)
                    .whenSearching("foobar",
                                   s -> s.inPeriod(documentStart.minusSeconds(1), documentEnd.plusSeconds(1))
                    ).<List<?>>expectResult(l -> !l.isEmpty());
        }
    }

    @Nested
    class SortingTests {

        private final Instant now = Instant.now();

        private final SomeDocument a = new SomeDocument().toBuilder().symbols("aaa").build();
        private final SomeDocument b = new SomeDocument();
        private final Given testFixture = TestFixture.create().atFixedTime(now)
                .givenDocument(a, "id1", "test", now)
                .givenDocument(b, "id2", "test", now.plusSeconds(1));

        @Test
        void sortTimeDescending() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test").streamHits().toList())
                    .<List<?>>expectResult(results -> results.size() == 2)
                    .<List<SearchHit<?>>>expectResult(results -> "id2".equals(results.get(0).getId())
                                                                 && "id1".equals(results.get(1).getId()));
        }

        @Test
        void sortTimeAscending() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test")
                                    .sortByTimestamp(false).streamHits().toList())
                    .<List<?>>expectResult(results -> results.size() == 2)
                    .<List<SearchHit<?>>>expectResult(results -> "id1".equals(results.get(0).getId())
                                                                 && "id2".equals(results.get(1).getId()));
        }

        @Test
        void sortOnField() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("symbols").fetchAll())
                    .expectResult(List.of(a, b));
        }

        @Test
        void sortOnFieldDesc() {
            testFixture.whenApplying(
                            fc -> fc.documentStore().search("test").sortBy("symbols", true).fetchAll())
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
                    .<Collection<?>>expectResult(result -> result.contains(a) && result.contains(b));
        }

        @Test
        void searchViaSearchable() {
            @Searchable(collection = "c1")
            class Annotated {
            }
            testFixture.whenApplying(fc -> FluxCapacitor.search(List.of(Annotated.class, "c2")).fetchAll())
                    .<Collection<?>>expectResult(result -> result.contains(a) && result.contains(b));
        }

        @Test
        void classWithoutSearchable() {
            class NotAnnotated {
            }
            testFixture.whenApplying(
                    fc -> FluxCapacitor.search(List.of(NotAnnotated.class, "c2")).fetchAll()).expectError();
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
                    .<Collection<?>>expectResult(result -> result.contains(a) && result.contains(b));
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
        Map<String, ?> status;

        public SomeDocument() {
            this.someId = ID;
            this.longNumber = new BigDecimal("106193501828612100");
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
        }
    }
}
