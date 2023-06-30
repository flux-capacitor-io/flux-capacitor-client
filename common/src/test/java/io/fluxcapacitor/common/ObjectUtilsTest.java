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

package io.fluxcapacitor.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @SuppressWarnings("unchecked")
    @Test
    void memoizeAllowsNullKeys() {
        Function<Object, Object> mockFunction = mock(Function.class);
        when(mockFunction.apply(any())).thenReturn("foo");
        ObjectUtils.MemoizingFunction<Object, Object> memoizingFunction = memoize(mockFunction);
        assertEquals("foo", memoizingFunction.apply(null));
        assertTrue(memoizingFunction.isCached(null));
        memoizingFunction.apply(null);
        verify(mockFunction, times(1)).apply(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void memoizeAllowsNullValues() {
        Function<Object, Object> mockFunction = mock(Function.class);
        ObjectUtils.MemoizingFunction<Object, Object> memoizingFunction = memoize(mockFunction);
        assertNull(memoizingFunction.apply("foo"));
        assertTrue(memoizingFunction.isCached("foo"));
        memoizingFunction.apply("foo");
        verify(mockFunction, times(1)).apply(any());
        assertNull(memoizingFunction.apply(null));
        verify(mockFunction, times(2)).apply(any());
    }
}