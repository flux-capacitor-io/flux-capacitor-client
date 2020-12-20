/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

import static java.lang.String.format;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class EventSourcedModel<T> implements Aggregate<T> {
    String id;
    @Builder.Default long sequenceNumber = -1L;
    String lastEventId;
    @Builder.Default Instant timestamp = Instant.now();
    T model;
    EventSourcedModel<T> previous;

    @Override
    public T get() {
        return model;
    }

    @Override
    public Aggregate<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException(format("Not allowed to apply a %s. The model is readonly.",
                                                       eventMessage));
    }
}
