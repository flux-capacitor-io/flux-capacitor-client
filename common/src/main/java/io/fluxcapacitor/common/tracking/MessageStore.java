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

package io.fluxcapacitor.common.tracking;

import io.fluxcapacitor.common.Monitored;
import io.fluxcapacitor.common.api.SerializedMessage;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

public interface MessageStore extends AutoCloseable, Monitored<List<SerializedMessage>>, HasMessageStore {

    default CompletableFuture<Void> append(SerializedMessage... messages) {
        return append(asList(messages));
    }

    CompletableFuture<Void> append(List<SerializedMessage> messages);

    default List<SerializedMessage> getBatch(Long minIndex, int maxSize) {
        return getBatch(minIndex, maxSize, false);
    }

    List<SerializedMessage> getBatch(Long minIndex, int maxSize, boolean inclusive);

    void setRetentionTime(Duration retentionPeriod);

    @SuppressWarnings("unchecked")
    default <T extends MessageStore> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(this.getClass())) {
            return (T) this;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    default void close() {
        //no op
    }

    @Override
    default MessageStore getMessageStore() {
        return this;
    }
}
