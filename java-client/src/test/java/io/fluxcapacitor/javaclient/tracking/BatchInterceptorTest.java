/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

import io.fluxcapacitor.common.api.SerializedMessage;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

public class BatchInterceptorTest {

    @Test
    public void testInvocationOrder() {
        List<Object> invokedInstances = new ArrayList<>();
        BatchInterceptor outerInterceptor = new BatchInterceptor() {
            @Override
            public Consumer<List<SerializedMessage>> intercept(Consumer<List<SerializedMessage>> consumer) {
                return messages -> {
                    invokedInstances.add(this);
                    consumer.accept(messages);
                };
            }
        };
        BatchInterceptor innerInterceptor = new BatchInterceptor() {
            @Override
            public Consumer<List<SerializedMessage>> intercept(Consumer<List<SerializedMessage>> consumer) {
                return messages -> {
                    invokedInstances.add(this);
                    consumer.accept(messages);
                };
            }
        };
        Consumer<List<SerializedMessage>> function = new Consumer<List<SerializedMessage>>() {
            @Override
            public void accept(List<SerializedMessage> messages) {
                invokedInstances.add(this);
            }
        };
        Consumer<List<SerializedMessage>> invocation = BatchInterceptor
                .join(Arrays.asList(outerInterceptor, innerInterceptor)).intercept(function);
        assertEquals(emptyList(), invokedInstances);
        invocation.accept(emptyList());
        assertEquals(Arrays.asList(outerInterceptor, innerInterceptor, function), invokedInstances);
    }
}