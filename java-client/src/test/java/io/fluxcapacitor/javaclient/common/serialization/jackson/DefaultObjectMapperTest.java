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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer.defaultObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultObjectMapperTest {

    @Test
    @SneakyThrows
    public void testIncludeNullCollectionsAsEmptyButExcludeOtherNulls() {
        TestModel mappedEmptyObject = defaultObjectMapper.convertValue(new TestModel(), TestModel.class);
        assertEquals(defaultObjectMapper.writeValueAsString(mappedEmptyObject), "{\"list\":[],\"set\":[]}");
    }

    @Data
    private static class TestModel {
        String string;
        List<String> list;
        Set<String> set;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> ignoredList;
    }
}
