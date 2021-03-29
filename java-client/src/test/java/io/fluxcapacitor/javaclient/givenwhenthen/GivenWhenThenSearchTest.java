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

import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.constraints.BetweenConstraint.below;
import static io.fluxcapacitor.common.api.search.constraints.ExistsConstraint.exists;
import static io.fluxcapacitor.common.api.search.constraints.FindConstraint.find;
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
        expectMatch(s -> s.find("see what"));
        expectMatch(s -> s.find("see wh*"));
        expectMatch(s -> s.find("* what"));
        expectMatch(s -> s.find("*e what"));
        expectNoMatch(s -> s.find("*a what"));
        expectMatch(s -> s.find("see what", "foo"));
        expectNoMatch(s -> s.find("bla bla"));
        expectNoMatch(s -> s.find("see what", "wrongField"));
    }

    @Test
    void testFieldMatching() {
        expectMatch(s -> s.match(SomeDocument.ID, "someId"));
        expectMatch(s -> s.match(SomeDocument.ID));
        expectMatch(s -> s.match(SomeDocument.ID, "someOtherField", "someId"));
        expectMatch(s -> s.match("Let's see what we can find", "foo"));
        expectNoMatch(s -> s.match(SomeDocument.ID.toLowerCase(), "someId"));
        expectNoMatch(s -> s.match(SomeDocument.ID, "wrongField"));
    }

    @Test
    void testNumberMatching() {
        //at least
        expectMatch(s -> s.atLeast(18.5, "someNumber"));
        expectMatch(s -> s.atLeast(20.5, "someNumber"));
        expectMatch(s -> s.atLeast(20.50, "someNumber"));
        expectNoMatch(s -> s.atLeast(18.5, "wrongField"));
        expectNoMatch(s -> s.atLeast(21, "someNumber"));

        //below
        expectMatch(s -> s.below(21.5, "someNumber"));
        expectNoMatch(s -> s.below(20, "someNumber"));
        expectNoMatch(s -> s.below(20.5, "someNumber"));

        //between
        expectMatch(s -> s.between(20, 30, "someNumber"));
        expectMatch(s -> s.between(20.5, 30, "someNumber"));
        expectNoMatch(s -> s.between(21, 30, "someNumber"));
        expectNoMatch(s -> s.between(20, 20.5, "someNumber"));

        assertThrows(Throwable.class, () -> expectNoMatch(s -> s.atLeast(18.5, null)), "Path should be required");
    }

    @Test
    void testExistsConstraint() {
        expectMatch(s -> s.constraint(exists("someId")));
    }

    @Test
    void testCombineConstraints() {
        expectMatch(s -> s.find("see wh*").below(21.5, "someNumber"));
        expectNoMatch(s -> s.not(find("see wh*").and(below(21.5, "someNumber"))));
        expectNoMatch(s -> s.find("see wh*", "wrongField").below(21.5, "someNumber"));
        expectMatch(s -> s.not(find("see wh*", "wrongField").and(below(21.5, "someNumber"))));
        expectMatch(s -> s.any(find("see wh*", "wrongField").or(below(21.5, "someNumber"))));
        expectNoMatch(s -> s.constraint(not(exists("someId"))));
    }

    @Test
    void testPathMatching() {
        expectMatch(s -> s.match(true, "booleans/first"));
        expectMatch(s -> s.match(true, "booleans/second"));
        expectMatch(s -> s.match(true, "*/second"));
        expectMatch(s -> s.match(true, "booleans/*"));
        expectMatch(s -> s.match(true, "booleans/**"));
        expectMatch(s -> s.match(true, "**"));
        expectNoMatch(s -> s.match(false, "**"));
        expectNoMatch(s -> s.match(false, "booleans/first"));
        expectMatch(s -> s.match(true, "booleans/third/inner"));
        expectMatch(s -> s.match(true, "booleans/*/inner"));
        expectMatch(s -> s.match(true, "booleans/**/inner"));
        expectMatch(s -> s.match(true, "**/inner"));
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

    private void expectMatch(UnaryOperator<Search> searchQuery) {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocuments("test", document).whenSearching("test", searchQuery)
                .expectResult(singletonList(document));
    }

    private void expectNoMatch(UnaryOperator<Search> searchQuery) {
        SomeDocument document = new SomeDocument();
        TestFixture.create().givenDocuments("test", document).whenSearching("test", searchQuery)
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
