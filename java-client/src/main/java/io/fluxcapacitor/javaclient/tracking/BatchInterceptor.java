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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.tracking.MessageBatch;

import java.util.List;
import java.util.function.Consumer;

@FunctionalInterface
public interface BatchInterceptor {

    static BatchInterceptor noOp() {
        return (c, t) -> c;
    }

    Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker);

    default void shutdown(Tracker tracker) {
        //no op
    }

    default BatchInterceptor andThen(BatchInterceptor nextInterceptor) {
        return new BatchInterceptor() {
            @Override
            public Consumer<MessageBatch> intercept(Consumer<MessageBatch> c, Tracker t) {
                return BatchInterceptor.this.intercept(nextInterceptor.intercept(c, t), t);
            }

            @Override
            public void shutdown(Tracker tracker) {
                nextInterceptor.shutdown(tracker);
                BatchInterceptor.this.shutdown(tracker);
            }
        };
    }

    static BatchInterceptor join(List<BatchInterceptor> interceptors) {
        return interceptors.stream().reduce(BatchInterceptor::andThen).orElse((c, t) -> c);
    }

}
