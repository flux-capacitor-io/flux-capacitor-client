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
import lombok.SneakyThrows;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;

public enum DefaultDocumentSerializer {

    INSTANCE;

    private static final int currentVersion = 0;

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
            return new Data<>(compress(packer.toByteArray()), document.getType(), document.getRevision(), "document");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize document", e);
        }
    }

    public Document deserialize(Data<byte[]> document) {
        if (!"document".equals(document.getFormat())) {
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
            return new Document(id, document.getType(), document.getRevision(), collection, timestamp, end, map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not deserialize document", e);
        }
    }

    @SneakyThrows
    private static void packTimestamp(Instant value, MessagePacker packer) {
        if (value == null) {
            packer.packNil();
        } else {
            packer.packLong(value.toEpochMilli());
        }
    }

    @SneakyThrows
    private static Instant unpackTimestamp(MessageUnpacker unpacker) {
        if (unpacker.getNextFormat().getValueType().isNilType()) {
            unpacker.unpackNil();
            return null;
        } else {
            return Instant.ofEpochMilli(unpacker.unpackLong());
        }
    }

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