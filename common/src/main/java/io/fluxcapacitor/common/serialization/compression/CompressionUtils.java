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

package io.fluxcapacitor.common.serialization.compression;

import lombok.NonNull;
import lombok.SneakyThrows;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

public class CompressionUtils {

    private static final LZ4Compressor lz4Compressor = LZ4Factory.fastestInstance().fastCompressor();
    private static final LZ4FastDecompressor lz4Decompressor = LZ4Factory.fastestInstance().fastDecompressor();

    public static byte[] compress(byte[] uncompressed) {
        return compress(uncompressed, CompressionAlgorithm.LZ4);
    }

    @SneakyThrows
    public static byte[] compress(byte[] uncompressed, @NonNull CompressionAlgorithm algorithm) {
        return switch (algorithm) {
            case NONE -> uncompressed;
            case LZ4 -> {
                byte[] compressed = lz4Compressor.compress(uncompressed);
                yield ByteBuffer.allocate(compressed.length + 4).putInt(uncompressed.length).put(compressed).array();
            }
            case GZIP -> {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                    zipStream.write(uncompressed);
                }
                yield byteStream.toByteArray();
            }
        };
    }

    public static byte[] decompress(byte[] compressed) {
        return decompress(compressed, CompressionAlgorithm.LZ4);
    }

    @SneakyThrows
    public static byte[] decompress(byte[] compressed, @NonNull CompressionAlgorithm algorithm) {
        return switch (algorithm) {
            case NONE -> compressed;
            case LZ4 -> {
                ByteBuffer buffer = ByteBuffer.wrap(compressed);
                ByteBuffer result = ByteBuffer.allocate(buffer.getInt());
                lz4Decompressor.decompress(buffer, result);
                yield result.array();
            }
            case GZIP -> {
                try (var gzipStream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    yield gzipStream.readAllBytes();
                } catch (ZipException ignored) {
                    yield compressed;
                }
            }
        };
    }

}
