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

import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class GivenWhenThenSearchTest {

    @Test
    void testExpectedDocuments() {
        SomeDocument someDocument = new SomeDocument();
        TestFixture.create().when(fc -> {
            fc.documentStore().index(someDocument, "test", "test");
            fc.documentStore().index("bla", "test2", "test");
        }).expectDocuments(List.of(someDocument, "bla"));
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
            this.booleans = Stream.of("first", "second", "third", "third", "fourth/key", "5").collect(
                    toMap(identity(), s -> true, (a, b) -> singletonMap("inner", true), LinkedHashMap::new));
            this.mapList = Arrays.asList(singletonMap(
                    "key1", new BigDecimal(10)), singletonMap("key2", "value2"));
            this.symbols = "Can you find slash in mid\\dle or \\front, or find <xml>? Anne-gre";
            this.weirdChars =
                    "ẏṏṳṙ ẇḕḭṙḊ ṮḕẌṮ ÄäǞǟĄ̈ą̈B̈b̈C̈c̈ËëḦḧÏïḮḯJ̈j̈K̈k̈L̈l̈M̈m̈N̈n̈ÖöȪȫǪ̈ǫ̈ṎṏP̈p̈Q̈q̈Q̣̈q̣̈R̈r̈S̈s̈T̈ẗÜüǕǖǗǘǙǚǛǜṲṳṺṻṲ̄ṳ̄ᴞV̈v̈ẄẅẌẍŸÿZ̈z̈ΪϊῒΐῗΫϋῢΰῧϔӒӓЁёӚӛӜӝӞӟӤӥЇїӦӧӪӫӰӱӴӵӸӹӬӭ";
        }
    }
}
