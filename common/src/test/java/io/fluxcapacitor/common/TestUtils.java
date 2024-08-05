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

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.SneakyThrows;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    private static final Random random = new Random();

    public static SerializedMessage createMessage() {
        return createMessages(1).get(0);
    }

    public static List<SerializedMessage> createMessages(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(20).putInt(i);
            byte[] randomBytes = new byte[16];
            random.nextBytes(randomBytes);
            return byteBuffer.put(randomBytes).array();
        }).map(bytes -> new SerializedMessage(new Data<>(bytes, "test", 0, null), Metadata.empty(), "someId", Clock.systemUTC().millis()))
                .collect(Collectors.toList());
    }

    public static void assertEqualMessages(List<SerializedMessage> expected, List<SerializedMessage> actual) {
        assertEquals(expected.size(), actual.size(), "Lists have a different size");
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).getData().getValue(), actual.get(i).getData().getValue());
        }
    }

    @SneakyThrows
    public static void sleepAWhile(int millis) {
        Thread.sleep(millis);
    }

    @SneakyThrows
    public static int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SneakyThrows
    public static void runWithSystemProperties(ThrowingRunnable runnable, String... propertyKeysAndValues) {
        callWithSystemProperties(() -> {
            runnable.run();
            return null;
        }, propertyKeysAndValues);
    }

    @SneakyThrows
    public static <V> V callWithSystemProperties(Callable<V> callable, String... propertyKeysAndValues) {
        if (propertyKeysAndValues.length %2 != 0) {
            throw new IllegalArgumentException("Expected pairs of keys and values");
        }
        try {
            for (int i = 0; i < propertyKeysAndValues.length; i += 2) {
                System.setProperty(propertyKeysAndValues[i], propertyKeysAndValues[i + 1]);
            }
            return callable.call();
        } finally {
            for (int i = 0; i < propertyKeysAndValues.length; i += 2) {
                System.clearProperty(propertyKeysAndValues[i]);
            }
        }
    }
}