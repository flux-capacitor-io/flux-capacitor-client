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

package io.fluxcapacitor.common.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Request implements JsonType {
    private static final AtomicLong nextRequestId = new AtomicLong();

    private final long requestId = nextRequestId.getAndIncrement();

    public long getRequestId() {
        return requestId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Request request = (Request) o;
        return requestId == request.requestId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }
}
