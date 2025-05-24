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

package io.fluxcapacitor.common.search;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.search.Document.Entry;
import io.fluxcapacitor.common.search.Document.Path;
import io.fluxcapacitor.common.serialization.compression.CompressionUtils;
import lombok.SneakyThrows;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;

/**
 * Default serializer for {@link Document} instances in the Flux platform.
 * <p>
 * This serializer uses the <a href="https://msgpack.org/">MessagePack</a> binary format to efficiently encode and decode documents
 * for storage or transport. It supports compression and is version-aware, allowing for future extension of the format.
 *
 * <h2>Serialization Format</h2>
 * The current (version 0) serialization includes:
 * <ul>
 *   <li>{@code int} format version (currently {@code 0})</li>
 *   <li>{@code String} document ID</li>
 *   <li>{@code Instant} timestamp (as epoch millis)</li>
 *   <li>{@code Instant} end timestamp (as epoch millis)</li>
 *   <li>{@code String} collection name</li>
 *   <li>{@code List<Entry>} entries and their associated {@code List<Path>}</li>
 * </ul>
 *
 * <p>
 * Compression is applied to the final byte output using {@link CompressionUtils} and marked with the format {@code document}
 * via {@link Data#DOCUMENT_FORMAT}.
 *
 * @see Document
 * @see Data
 */
public enum DefaultDocumentSerializer {
    INSTANCE;

    private static final int currentVersion = 0;

    /**
     * Serializes the given {@link Document} into a compressed binary {@link Data} container.
     *
     * @param document the document to serialize
     * @return the serialized form of the document
     * @throws IllegalArgumentException if the document cannot be serialized
     */
    public Data<byte[]> serialize(Document document) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            Map<Entry, List<Path>> map = document.getEntries();
            packer.packInt(currentVersion).packString(document.getId());
            packTimestamp(document.getTimestamp(), packer);
            packTimestamp(document.getEnd(), packer);
            packer.packString(document.getCollection()).packArrayHeader(map.size());
            for (Map.Entry<Entry, List<Path>> e : map.entrySet()) {
                packer.packByte(e.getKey().getType().serialize());
                packer.packString(e.getKey().getValue());
                List<Path> keys = e.getValue();
                packer.packArrayHeader(keys.size());
                for (Path key : keys) {
                    packer.packString(key.getValue());
                }
            }
            return new Data<>(compress(packer.toByteArray()), document.getType(), document.getRevision(), Data.DOCUMENT_FORMAT);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize document", e);
        }
    }

    /**
     * Deserializes a {@link Data} blob containing a compressed, binary {@link Document}.
     * The data must be in the correct format and contain a supported version.
     *
     * @param document the data to deserialize
     * @return the reconstructed {@link Document}
     * @throws IllegalArgumentException if the data cannot be parsed or is invalid
     */
    public Document deserialize(Data<byte[]> document) {
        if (!canDeserialize(document)) {
            throw new IllegalArgumentException("Unsupported data format: " + document.getFormat());
        }
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(decompress(document.getValue()))) {
            int version = unpacker.unpackInt();
            if (version != 0) {
                throw new IllegalArgumentException("Unsupported document revision: " + version);
            }
            String id = unpacker.unpackString();
            Instant timestamp = unpackTimestamp(unpacker);
            Instant end = unpackTimestamp(unpacker);
            String collection = unpacker.unpackString();
            Map<Entry, List<Path>> map = new LinkedHashMap<>();
            int size = unpacker.unpackArrayHeader();
            for (int i = 0; i < size; i++) {
                Entry value = new Entry(Document.EntryType.deserialize(unpacker.unpackByte()), unpacker.unpackString());
                int keysCount = unpacker.unpackArrayHeader();
                List<Path> keys = new ArrayList<>(keysCount);
                map.put(value, keys);
                for (int j = 0; j < keysCount; j++) {
                    keys.add(new Path(unpacker.unpackString()));
                }
            }
            return new Document(id, document.getType(), document.getRevision(), collection, timestamp, end, map,
                                null, Collections.emptySet(), Collections.emptySet());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not deserialize document", e);
        }
    }

    /**
     * Checks whether the given {@link Data} object is in a format that this serializer can deserialize.
     *
     * @param data the data blob
     * @return {@code true} if the format is {@code document}, {@code false} otherwise
     */
    public boolean canDeserialize(Data<byte[]> data) {
        return Data.DOCUMENT_FORMAT.equals(data.getFormat());
    }

    /**
     * Writes a timestamp into the MessagePack stream, using {@code packLong(epochMillis)} or {@code packNil()} if null.
     */
    @SneakyThrows
    private static void packTimestamp(Instant value, MessagePacker packer) {
        if (value == null) {
            packer.packNil();
        } else {
            packer.packLong(value.toEpochMilli());
        }
    }

    /**
     * Reads an optional timestamp from the MessagePack stream.
     *
     * @param unpacker the unpacker instance
     * @return the unpacked {@link Instant}, or {@code null} if the field is missing
     */
    @SneakyThrows
    private static Instant unpackTimestamp(MessageUnpacker unpacker) {
        if (unpacker.getNextFormat().getValueType().isNilType()) {
            unpacker.unpackNil();
            return null;
        } else {
            return Instant.ofEpochMilli(unpacker.unpackLong());
        }
    }

    /**
     * Reads an optional long value from the MessagePack stream.
     *
     * @param unpacker the unpacker instance
     * @return the unpacked long value, or {@code null} if the field is missing
     */
    @SneakyThrows
    private static Long unpackLong(MessageUnpacker unpacker) {
        if (unpacker.getNextFormat().getValueType().isNilType()) {
            unpacker.unpackNil();
            return null;
        } else {
            return unpacker.unpackLong();
        }
    }
}