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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.SerializedObject;

import java.util.List;
import java.util.stream.Stream;

/**
 * Mechanism to convert objects to a byte array and vice versa.
 */
public interface Serializer extends ContentFilter {

    /**
     * Serializes an object to a {@link Data} object containing a byte array.
     *
     * @param object The instance to serialize
     * @return Data object containing byte array representation of the object
     * @throws SerializationException if serialization fails
     */
    default Data<byte[]> serialize(Object object) {
        return serialize(object, null);
    }

    /**
     * Serializes an object using the given desired {@code format} to a {@link Data} object containing a byte array. If
     * format is {@code null} the default format of this serializer may be used.
     *
     * @param object The instance to serialize
     * @return Data object containing byte array representation of the object
     * @throws SerializationException if serialization fails
     */
    Data<byte[]> serialize(Object object, String format);

    /**
     * Upcasts and deserializes the given {@link Data} object to an object of type T. If the input data cannot be
     * deserialized to a single result (due to upcasting) a {@link DeserializationException} is thrown.
     *
     * @param data Data to deserialize
     * @param <T>  Type of object to deserialize to
     * @return Object resulting from the deserialization
     * @throws DeserializationException if deserialization fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> T deserialize(SerializedObject<byte[]> data) {
        List<DeserializingObject<T, ?>> list = deserialize((Stream) Stream.of(data), UnknownTypeStrategy.FAIL).toList();
        if (list.size() != 1) {
            throw new DeserializationException(
                    String.format("Invalid deserialization result for a '%s'. Expected a single object but got %s",
                                  data, list.stream().map(DeserializingObject::getClass).toList()));
        }
        return list.getFirst().getPayload();
    }

    /**
     * Upcasts and deserializes the given {@link Data} object to an object of type T. If the input data cannot be
     * deserialized to a single result (due to upcasting) a {@link DeserializationException} is thrown.
     *
     * @param data Data to deserialize
     * @param <T>  Type of object to deserialize to
     * @return Object resulting from the deserialization
     * @throws DeserializationException if deserialization fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> T deserialize(SerializedObject<byte[]> data, Class<T> type) {
        List<DeserializingObject<T, ?>> list = deserialize((Stream) Stream.of(data), UnknownTypeStrategy.AS_INTERMEDIATE).toList();
        if (list.size() != 1) {
            throw new DeserializationException(
                    String.format("Invalid deserialization result for a '%s'. Expected a single object but got %s",
                                  data, list.stream().map(DeserializingObject::getClass).toList()));
        }
        return list.getFirst().getPayloadAs(type);
    }

    /**
     * Upcasts and deserializes a stream of serialized objects. Each result in the output stream contains both a
     * provider for the deserialized object and the serialized object after upcasting that is used as the source of the
     * deserialized object.
     * <p>
     * Deserialization is performed lazily. This means that actual conversion for a given result in the output stream
     * only happens if {@link DeserializingObject#getPayload()} is invoked on the result. This has the advantage that a
     * caller can inspect what type will be returned via {@link DeserializingObject#getSerializedObject()} before
     * deciding to go through with the deserialization.
     * <p>
     * You can specify whether deserialization of a result in the output stream should fail with a
     * {@link DeserializationException} if a type is unknown (not a class). It is up to the implementation to determine
     * what should happen if a type is unknown but the {@code failOnUnknownType} flag is false.
     *
     * @param dataStream          data input stream to deserialize
     * @param unknownTypeStrategy value that determines what to do when encountering unknown types
     * @param <I>                 the type of the serialized object
     * @return a stream containing deserialization results
     */
    <I extends SerializedObject<byte[]>> Stream<DeserializingObject<byte[], I>> deserialize(
            Stream<I> dataStream, UnknownTypeStrategy unknownTypeStrategy);

    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType) {
        return deserializeMessages(dataStream, messageType, (String) null);
    }

    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType, String topic) {
        return deserializeMessages(dataStream, messageType, topic, UnknownTypeStrategy.AS_INTERMEDIATE);
    }

    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType, UnknownTypeStrategy unknownTypeStrategy) {
        return deserializeMessages(dataStream, messageType, null, unknownTypeStrategy);
    }

    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType, String topic, UnknownTypeStrategy unknownTypeStrategy) {
        return deserialize(dataStream, unknownTypeStrategy).map(s -> new DeserializingMessage(s, messageType, topic));
    }

    default DeserializingMessage deserializeMessage(SerializedMessage message, MessageType messageType) {
        return deserializeMessages(Stream.of(message), messageType).findAny().orElseThrow();
    }

    <V> V convert(Object value, Class<V> type);

    <V> V clone(Object value);

    Serializer registerTypeCaster(String oldType, String newType);

    String upcastType(String type);

    Object downcast(Object object, int desiredRevision);

    Object downcast(Data<?> data, int desiredRevision);
}
