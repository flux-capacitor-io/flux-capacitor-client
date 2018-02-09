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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;

@AllArgsConstructor
public class MessageSerializer {
    private final Function<Message, SerializedMessage> serializer;
    private final Function<SerializedMessage, Message> deserializer;
    private final MessageType messageType;

    public MessageSerializer(Serializer serializer, DispatchInterceptor dispatchInterceptor, MessageType messageType) {
        this(dispatchInterceptor.interceptDispatch(
                m -> new SerializedMessage(serializer.serialize(m.getPayload()), m.getMetadata())),
             s -> new Message(serializer.deserialize(s.getData()), s.getMetadata(), messageType), messageType);
    }

    public MessageSerializer(Serializer serializer, MessageType messageType) {
        this(m -> new SerializedMessage(serializer.serialize(m.getPayload()), m.getMetadata()),
             s -> new Message(serializer.deserialize(s.getData()), s.getMetadata(), messageType), messageType);
    }

    public SerializedMessage serialize(Message message) {
        return serializer.apply(message);
    }

    public Message deserialize(SerializedMessage serializedMessage) {
        return deserializer.apply(serializedMessage);
    }

    public SerializedMessage serialize(Object payload, Metadata metadata) {
        return serialize(new Message(payload, metadata, messageType));
    }
}
