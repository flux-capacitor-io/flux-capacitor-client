/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class ConsistentHashing {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final HashFunction MURMUR3_32 = Hashing.murmur3_32();
    private static final Function<String, Long> defaultHashFunction = s -> MURMUR3_32.hashString(s, UTF8).padToLong();

    public static int computeSegment(String routingKey) {
        return computeSegment(routingKey, defaultHashFunction, 1024);
    }

    public static int computeSegment(String routingKey, int maxSegments) {
        return computeSegment(routingKey, defaultHashFunction, maxSegments);
    }

    public static int computeSegment(String routingKey, Function<String, Long> hashFunction, int segments) {
        return Hashing.consistentHash(hashFunction.apply(routingKey), segments);
    }

}
