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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchInterceptorTest {

    @Test
    void testInvocationOrder() {
        List<Object> invokedInstances = new ArrayList<>();
        BatchInterceptor outerInterceptor = new BatchInterceptor() {
            @Override
            public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
                return messages -> {
                    invokedInstances.add(this);
                    consumer.accept(messages);
                };
            }
        };
        BatchInterceptor innerInterceptor = new BatchInterceptor() {
            @Override
            public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
                return messages -> {
                    invokedInstances.add(this);
                    consumer.accept(messages);
                };
            }
        };
        Consumer<MessageBatch> function = new Consumer<MessageBatch>() {
            @Override
            public void accept(MessageBatch messages) {
                invokedInstances.add(this);
            }
        };
        ConsumerConfiguration configuration = ConsumerConfiguration.getDefault(
                MessageType.COMMAND);
        Consumer<MessageBatch> invocation = BatchInterceptor.join(Arrays.asList(outerInterceptor, innerInterceptor))
                .intercept(function, new Tracker("test", "0", configuration, null));
        assertEquals(emptyList(), invokedInstances);
        invocation.accept(new MessageBatch(new int[]{0, 1}, emptyList(), 0L));
        assertEquals(Arrays.asList(outerInterceptor, innerInterceptor, function), invokedInstances);
    }
}