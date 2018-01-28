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

import java.util.List;
import java.util.function.Consumer;

@FunctionalInterface
public interface BatchInterceptor {

    Consumer<List<SerializedMessage>> intercept(Consumer<List<SerializedMessage>> consumer);

    default BatchInterceptor merge(BatchInterceptor outerBatchInterceptor) {
        return f -> outerBatchInterceptor.intercept(intercept(f));
    }

    static BatchInterceptor join(List<BatchInterceptor> interceptors) {
        return interceptors.stream().reduce((a, b) -> b.merge(a)).orElse(c -> c);
    }

}
