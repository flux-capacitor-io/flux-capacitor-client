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

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.atLeast;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.below;
import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.between;
import static io.fluxcapacitor.common.api.search.constraints.ExistsConstraint.exists;
import static io.fluxcapacitor.common.api.search.constraints.FindConstraint.find;
import static io.fluxcapacitor.common.api.search.constraints.MatchConstraint.match;
import static io.fluxcapacitor.common.api.search.constraints.NotConstraint.not;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GivenWhenThenSearchTest {

    @Test
    void testPhraseMatching() {
        expectMatch(find("see what"));
        expectMatch(find("see wh*"));
        expectMatch(find("* what"));
        expectMatch(find("*e what"));
        expectNoMatch(find("*a what"));
        expectMatch(find("see what", "foo"));
        expectNoMatch(find("bla bla"));
        expectNoMatch(find("see what", "wrongField"));
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
        expectMatch(find("see wh*"), below(21.5, "someNumber"));
        expectNoMatch(not(find("see wh*").and(below(21.5, "someNumber"))));
        expectNoMatch(find("see wh*", "wrongField"), below(21.5, "someNumber"));
        expectMatch(not(find("see wh*", "wrongField").and(below(21.5, "someNumber"))));
        expectMatch(any(find("see wh*", "wrongField").or(below(21.5, "someNumber"))));
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
    private static class SomeDocument {
        private static final String ID = "123A45B67c";
        String someId = ID;
        String foo = "Let's see what we can find";
        BigDecimal someNumber = new BigDecimal("20.5");
        Map<String, Object> booleans = Stream.of("first", "second", "third", "third").collect(
                toMap(identity(), s -> true, (a, b) -> singletonMap("inner", true), LinkedHashMap::new));
    }
}
