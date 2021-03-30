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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;

@AllArgsConstructor
public class EventStoreSerializer {
    private final Function<Message, SerializedMessage> serializer;
    private final Serializer deserializer;

    public EventStoreSerializer(Serializer serializer, DispatchInterceptor dispatchInterceptor) {
        this(dispatchInterceptor.interceptDispatch(m -> m.serialize(serializer), EVENT), serializer);
    }

    public EventStoreSerializer(Serializer serializer) {
        this(m -> m.serialize(serializer), serializer);
    }

    public SerializedMessage serialize(Message message) {
        return serializer.apply(message);
    }

    public Stream<DeserializingMessage> deserializeDomainEvents(Stream<SerializedMessage> messageStream) {
        return deserializer.deserializeMessages(messageStream, true, EVENT);
    }

    public <V> V convert(Object value, Class<V> type) {
        return deserializer.convert(value, type);
    }
}
