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

package io.fluxcapacitor.common.api.search.constraints;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.serialization.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstraintParsingTest {

    @Test
    void testQueryDecompose() {
        String query =
                " k*aas* A OR (B ORfoo$ \n*bar* OR(!notThis* (\"*cheese (is) OR very tasty&\") OR !(chick=fox)) mouse dog OR cat hare ) ";
        Constraint constraint = AllConstraint.all(QueryConstraint.query(query).decompose());
        Object expected = JsonUtils.fromFile("findConstraintDecomposed.json");
        assertEquals(JsonUtils.asPrettyJson(expected), JsonUtils.asPrettyJson(constraint));
    }

    @Test
    void testLookAheadWithOperators() {
        String query =
                " k*aas* **A OR (B ORfoo$ \n*bar* OR(!notThis* (\"*cheese (is) OR very tasty&\") OR !(chick=fox)) mouse dog OR cat** hare ) ";
        Constraint constraint = AllConstraint.all(LookAheadConstraint.lookAhead(query).decompose());
        Object expected = JsonUtils.fromFile( "lookAheadConstraintDecomposed.json");
        assertEquals(JsonUtils.asPrettyJson(expected), JsonUtils.asPrettyJson(constraint));
    }
}