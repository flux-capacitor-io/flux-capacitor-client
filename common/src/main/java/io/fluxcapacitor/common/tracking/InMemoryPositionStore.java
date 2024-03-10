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

import io.fluxcapacitor.common.api.tracking.Position;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPositionStore implements PositionStore {

    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex) {
        positions.compute(consumer, (p, oldPosition) -> {
            if (oldPosition == null) {
                oldPosition = Position.newPosition();
            }
            return oldPosition.merge(new Position(segment, lastIndex));
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> resetPosition(String consumer, long lastIndex) {
        positions.put(consumer, new Position(new int[]{0, Position.MAX_SEGMENT}, lastIndex));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Position position(String consumer) {
        return positions.computeIfAbsent(consumer, c -> Position.newPosition());
    }

    @Override
    public void close() {
        //no op
    }

}
