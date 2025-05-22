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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.SerializedObject;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

/**
 * Mechanism to serialize and deserialize objects to and from {@code byte[]} representations.
 * <p>
 * A {@code Serializer} transforms Java objects into {@link Data} containers (holding raw byte arrays) and restores them
 * back, optionally using format hints or handling type revisions via upcasting/downcasting.
 * </p>
 *
 * <p>
 * It also provides lazy deserialization and registration hooks for custom (de)casters. This makes it central to Flux
 * Capacitor’s persistence, transport, and replay systems.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Serialize objects with optional format hints</li>
 *   <li>Deserialize to objects or messages, lazily if needed</li>
 *   <li>Support revisioned types via upcasting and downcasting</li>
 *   <li>Enable flexible error strategies for unknown types</li>
 * </ul>
 *
 * @see Data
 * @see DeserializingObject
 * @see SerializedObject
 * @see DeserializationException
 * @see SerializationException
 */
public interface Serializer extends ContentFilter {

    /**
     * Serializes the given object to a {@link Data} wrapper using the default format.
     *
     * @param object the object to serialize
     * @return the serialized object wrapped in {@link Data}
     * @throws SerializationException if serialization fails
     */
    default Data<byte[]> serialize(Object object) {
        return serialize(object, null);
    }

    /**
     * Serializes the given object into a {@link Data} wrapper using the specified format.
     *
     * @param object the object to serialize
     * @param format the desired serialization format (e.g. \"json\"); may be {@code null}
     * @return serialized object as {@link Data}
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
        List<DeserializingObject<T, ?>> list =
                deserialize((Stream) Stream.of(data), UnknownTypeStrategy.AS_INTERMEDIATE).toList();
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

    /**
     * Deserializes a stream of {@link SerializedMessage} into {@link DeserializingMessage} instances with the specified
     * {@link MessageType}.
     *
     * @param dataStream  the stream of messages
     * @param messageType the type of message (COMMAND, EVENT, etc.)
     * @return stream of deserialized messages
     */
    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType) {
        return deserializeMessages(dataStream, messageType, (String) null);
    }

    /**
     * Deserializes a stream of {@link SerializedMessage} into {@link DeserializingMessage} instances with the specified
     * {@link MessageType}.
     *
     * @param dataStream  the stream of messages
     * @param messageType the type of message (COMMAND, EVENT, etc.)
     * @param topic       the topic of the message if the type is CUSTOM or DOCUMENT, otherwise {@code null}
     * @return stream of deserialized messages
     */
    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType, String topic) {
        return deserializeMessages(dataStream, messageType, topic, UnknownTypeStrategy.AS_INTERMEDIATE);
    }

    /**
     * Deserializes a stream of {@link SerializedMessage} into {@link DeserializingMessage} instances with the specified
     * {@link MessageType}.
     *
     * @param dataStream          the stream of messages
     * @param messageType         the type of message (COMMAND, EVENT, etc.)
     * @param unknownTypeStrategy value that determines what to do when encountering unknown types
     * @return stream of deserialized messages
     */
    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType,
                                                             UnknownTypeStrategy unknownTypeStrategy) {
        return deserializeMessages(dataStream, messageType, null, unknownTypeStrategy);
    }

    /**
     * Deserializes a stream of {@link SerializedMessage} into {@link DeserializingMessage} instances with the specified
     * {@link MessageType}.
     *
     * @param dataStream          the stream of messages
     * @param messageType         the type of message (COMMAND, EVENT, etc.)
     * @param topic               the topic of the message if the type is CUSTOM or DOCUMENT, otherwise {@code null}
     * @param unknownTypeStrategy value that determines what to do when encountering unknown types
     * @return stream of deserialized messages
     */
    default Stream<DeserializingMessage> deserializeMessages(Stream<SerializedMessage> dataStream,
                                                             MessageType messageType, String topic,
                                                             UnknownTypeStrategy unknownTypeStrategy) {
        return deserialize(dataStream, unknownTypeStrategy).map(s -> new DeserializingMessage(s, messageType, topic));
    }

    /**
     * Deserializes a single {@link SerializedMessage} into a {@link DeserializingMessage}. If the input data cannot be
     * deserialized to a single result (due to upcasting) a {@link DeserializationException} is thrown.
     *
     * @param message     the message to deserialize
     * @param messageType the message type
     * @return the deserialized message
     */
    default DeserializingMessage deserializeMessage(SerializedMessage message, MessageType messageType) {
        return deserializeMessages(Stream.of(message), messageType).findAny()
                .orElseThrow(DeserializationException::new);
    }

    /**
     * Converts a given object to another type using the serializer's object mapping rules.
     *
     * @param value the input value
     * @param type  the target type
     * @param <V>   the result type
     * @return the converted value
     */
    <V> V convert(Object value, Type type);

    /**
     * Creates a deep copy of the given object using serialization.
     *
     * @param value the object to clone
     * @param <V> the type of the value
     * @return a deep copy
     */
    <V> V clone(Object value);

    /**
     * Registers one or more upcaster candidates.
     *
     * @param casterCandidates beans with upcasting logic
     * @return a registration handle
     */
    Registration registerUpcasters(Object... casterCandidates);

    /**
     * Registers one or more downcaster candidates.
     *
     * @param casterCandidates beans with downcasting logic
     * @return a registration handle
     */
    Registration registerDowncasters(Object... casterCandidates);

    /**
     * Registers upcasters and downcasters in one step.
     *
     * @param casterCandidates beans with casting logic
     * @return a merged registration handle
     */
    default Registration registerCasters(Object... casterCandidates) {
        return registerUpcasters(casterCandidates).merge(registerDowncasters(casterCandidates));
    }

    /**
     * Registers a mapping from an old type identifier to a new one.
     *
     * @param oldType the legacy type name
     * @param newType the canonical or updated type name
     * @return a registration handle
     */
    Registration registerTypeCaster(String oldType, String newType);

    /**
     * Returns the upcasted type name for a legacy type identifier.
     *
     * @param type the original type
     * @return the remapped (or unchanged) type name
     */
    String upcastType(String type);

    /**
     * Downcasts the given object to a previous revision.
     *
     * @param object the object to downcast
     * @param desiredRevision the target revision
     * @return a revisioned form of the object
     */
    Object downcast(Object object, int desiredRevision);

    /**
     * Downcasts a {@link Data} object to the specified revision level.
     *
     * @param data the serialized data
     * @param desiredRevision the target revision number
     * @return a transformed object matching the older revision
     */
    Object downcast(Data<?> data, int desiredRevision);
}
