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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import io.fluxcapacitor.common.api.Data;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DowncasterChainTest {

    private final Downcasters downcasters = new Downcasters();
    private final Caster<Data<String>, Data<String>> subject = DefaultCasterChain.createDowncaster(List.of(downcasters), String.class);

    @Test
    void inputIsDowncasted() {
        Data<String> input = new Data<>("bar", "mapPayload", 1, null);
        Stream<? extends Data<String>> result = subject.cast(Stream.of(input));
        assertEquals(List.of(new Data<>(downcasters.mapPayload(input.getValue()),
                                        "mapPayload", 0, null)), result.collect(toList()));
    }

    private static class Downcasters {

        @Downcast(type = "mapPayload", revision = 1)
        public String mapPayload(String input) {
            return "foo";
        }

    }


}