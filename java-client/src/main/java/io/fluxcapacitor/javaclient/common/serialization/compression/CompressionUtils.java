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

package io.fluxcapacitor.javaclient.common.serialization.compression;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.ByteBuffer;

public class CompressionUtils {

    private static final LZ4Compressor lz4Compressor = LZ4Factory.fastestInstance().fastCompressor();
    private static final LZ4FastDecompressor lz4Decompressor = LZ4Factory.fastestInstance().fastDecompressor();

    public static byte[] compress(byte[] uncompressed, CompressionAlgorithm algorithm) {
        if (algorithm == null) {
            return uncompressed;
        }
        switch (algorithm) {
            case LZ4:
                byte[] compressed = lz4Compressor.compress(uncompressed);
                return ByteBuffer.allocate(compressed.length + 4).putInt(uncompressed.length).put(compressed).array();
        }
        throw new UnsupportedOperationException("Unsupported compression algorithm: " + algorithm);
    }

    public static byte[] decompress(byte[] compressed, CompressionAlgorithm algorithm) {
        if (algorithm == null) {
            return compressed;
        }
        switch (algorithm) {
            case LZ4:
                ByteBuffer buffer = ByteBuffer.wrap(compressed);
                ByteBuffer result = ByteBuffer.allocate(buffer.getInt());
                lz4Decompressor.decompress(buffer, result);
                return result.array();
        }
        throw new UnsupportedOperationException("Unsupported compression algorithm: " + algorithm);
    }

}
