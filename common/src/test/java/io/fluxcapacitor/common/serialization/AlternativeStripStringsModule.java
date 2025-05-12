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

package io.fluxcapacitor.common.serialization;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.SneakyThrows;

public class AlternativeStripStringsModule extends SimpleModule {

    @Override
    public void setupModule(SetupContext context) {
        addDeserializer(String.class, new BlankStringsToNullDeserializer());
        super.setupModule(context);
    }

    private static class BlankStringsToNullDeserializer extends StdScalarDeserializer<String> {
        public BlankStringsToNullDeserializer() {
            super(String.class);
        }

        @Override
        @SneakyThrows
        public String deserialize(JsonParser parser, DeserializationContext context) {
            String result = parser.getValueAsString();
            return result == null || result.isBlank() ? null : result; //don't trim the final result
        }
    }
}
