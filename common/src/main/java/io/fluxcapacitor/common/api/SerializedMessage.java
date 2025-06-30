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

package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.With;

/**
 * Represents a fully serialized message for transmission or storage within the Flux platform.
 * <p>
 * A {@code SerializedMessage} wraps the binary {@link Data} of a message payload, along with associated
 * {@link Metadata} and optional routing and tracking information such as {@code index}, {@code segment}, and
 * {@code requestId}.
 * </p>
 *
 * <p>
 * This class is the wire format and persistence format for messages. It implements {@link SerializedObject} to expose
 * type and revision metadata, and {@link HasMetadata} to allow downstream access to structured metadata.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Encapsulates payload as serialized {@code byte[]} via {@link Data}</li>
 *   <li>Immutable-style updates via {@code @With} for metadata, segment, etc.</li>
 *   <li>Tracks message origin and target via {@code source} and {@code target}</li>
 *   <li>Supports custom revision control via {@code originalRevision}, set before upcasting</li>
 * </ul>
 *
 * <h2>Tracking Fields</h2>
 * <ul>
 *   <li><b>segment</b> – the segment of the message log this message belongs to (used for partitioning)</li>
 *   <li><b>index</b> – the index of this message in the log, used for ordering and deduplication</li>
 *   <li><b>requestId</b> – identifier that ties a response to its originating request</li>
 * </ul>
 *
 * <h2>Typical Use</h2>
 * Serialized messages are produced by serializing a {@code Message} using a {@code Serializer},
 * and are then stored, indexed, transmitted, or routed based on metadata and log location.
 *
 * @see Data
 * @see Metadata
 * @see SerializedObject
 */
@lombok.Data
@AllArgsConstructor
public class SerializedMessage implements SerializedObject<byte[]>, HasMetadata {

    /**
     * The serialized representation of the message payload.
     */
    @NonNull
    private Data<byte[]> data;

    /**
     * Structured metadata associated with the message, such as headers or routing info.
     */
    @With
    private Metadata metadata;

    /**
     * The segment number for this message, used for partitioning and consistent hashing.
     */
    @With
    private Integer segment;

    /**
     * The index of the message within its segment. Used for message ordering and deduplication.
     */
    private Long index;

    /**
     * The identifier of the source that published this message.
     */
    private String source;

    /**
     * The optional target ID that this message is addressed to.
     */
    private String target;

    /**
     * An optional request ID linking this message to a request-response interaction.
     */
    private Integer requestId;

    /**
     * The timestamp when this message was created, in epoch milliseconds.
     */
    private Long timestamp;

    /**
     * The unique identifier of the message.
     */
    private String messageId;

    /**
     * If set, contains the original revision of the message prior to upcasting.
     */
    private transient Integer originalRevision;

    public SerializedMessage(Data<byte[]> data, Metadata metadata, String messageId, Long timestamp) {
        this.data = data;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.messageId = messageId;
    }

    /**
     * Returns the original revision of the payload object.
     * <p>
     * If {@code originalRevision} was explicitly set before upcasting, it is returned. Otherwise, the revision is
     * delegated to {@link Data#getRevision()}.
     *
     * @return the original revision of the serialized payload
     */
    public int getOriginalRevision() {
        return originalRevision == null ? data.getRevision() : originalRevision;
    }

    @Override
    public Data<byte[]> data() {
        return data;
    }

    @Override
    public SerializedMessage withData(@NonNull Data<byte[]> data) {
        return this.data == data ? this : new SerializedMessage(data, this.metadata, this.segment, this.index,
                                                                this.source, this.target, this.requestId,
                                                                this.timestamp, this.messageId,
                                                                this.data.getRevision());
    }

    @Override
    @JsonIgnore
    public int getRevision() {
        return SerializedObject.super.getRevision();
    }

    @Override
    @JsonIgnore
    public String getType() {
        return SerializedObject.super.getType();
    }

    /**
     * Returns the number of bytes of the serialized message payload.
     */
    public long bytes() {
        return data.getValue().length;
    }
}
