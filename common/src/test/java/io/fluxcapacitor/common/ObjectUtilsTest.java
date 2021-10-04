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

package io.fluxcapacitor.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectUtilsTest {
    @Test
    void testDeduplicateList() {
        List<Object> list = List.of("a", "b", "b", "c", "b", "a", "a");
        assertEquals(List.of("c", "b", "a"), ObjectUtils.deduplicate(list));
    }

    @Test
    void testDeduplicateListKeepFirst() {
        List<Object> list = List.of("a", "b", "b", "c", "b", "a", "a");
        assertEquals(List.of("a", "b", "c"), ObjectUtils.deduplicate(list, Function.identity(), true));
    }
}