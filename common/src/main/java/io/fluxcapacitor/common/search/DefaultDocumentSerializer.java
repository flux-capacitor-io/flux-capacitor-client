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

package io.fluxcapacitor.common.search;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.search.Document.Entry;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
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
            Map<Entry, List<String>> map = document.getEntries();
            packer.packInt(currentVersion).packString(document.getId())
                    .packLong(document.getTimestamp().toEpochMilli()).packString(document.getCollection())
                    .packArrayHeader(map.size());
            for (Map.Entry<Entry, List<String>> e : map.entrySet()) {
                packer.packByte(e.getKey().getType().serialize());
                packer.packString(e.getKey().getValue());
                List<String> keys = e.getValue();
                packer.packArrayHeader(keys.size());
                for (String key : keys) {
                    packer.packString(key);
                }
            }
            return new Data<>(compress(packer.toByteArray()), document.getType(), document.getRevision(), "document");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize document", e);
        }
    }

    public Document deserialize(Data<byte[]> document) {
        if (!document.getFormat().equals("document")) {
            throw new IllegalArgumentException("Unsupported data format: " + document.getFormat());
        }
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(decompress(document.getValue()))) {
            int version = unpacker.unpackInt();
            if (version != 0) {
                throw new IllegalArgumentException("Unsupported document revision: " + version);
            }
            String id = unpacker.unpackString();
            Instant timestamp = Instant.ofEpochMilli(unpacker.unpackLong());
            String collection = unpacker.unpackString();
            Map<Entry, List<String>> map = new LinkedHashMap<>();
            int size = unpacker.unpackArrayHeader();
            for (int i = 0; i < size; i++) {
                Entry value = new Entry(Document.EntryType.deserialize(unpacker.unpackByte()), unpacker.unpackString());
                int keysCount = unpacker.unpackArrayHeader();
                List<String> keys = new ArrayList<>(keysCount);
                map.put(value, keys);
                for (int j = 0; j < keysCount; j++) {
                    keys.add(unpacker.unpackString());
                }
            }
            return new Document(id, document.getType(), document.getRevision(), collection, timestamp, map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not deserialize document", e);
        }
    }

    public SerializedDocument asSerializedDocument(Data<byte[]> document) {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(decompress(document.getValue()))) {
            int version = unpacker.unpackInt();
            if (version != currentVersion) {
                throw new IllegalArgumentException();
            }
            String id = unpacker.unpackString();
            long timestamp = unpacker.unpackLong();
            String collection = unpacker.unpackString();
            return new SerializedDocument(id, timestamp, collection, document, null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert bytes to SerializedDocument", e);
        }
    }

}