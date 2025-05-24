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

/**
 * Utility class for compressing and decompressing byte arrays using common compression algorithms.
 * <p>
 * Supports multiple algorithms including:
 * <ul>
 *   <li>{@link CompressionAlgorithm#LZ4} – fast compression optimized for speed</li>
 *   <li>{@link CompressionAlgorithm#GZIP} – standard GZIP format for interoperability</li>
 *   <li>{@link CompressionAlgorithm#NONE} – pass-through mode (no compression)</li>
 * </ul>
 *
 * <h2>LZ4 Support</h2>
 * When compressing with LZ4, the output includes a 4-byte prefix that encodes the length of the original
 * (uncompressed) data. This prefix is required during decompression.
 *
 * <h2>GZIP Support</h2>
 * GZIP compression and decompression are compatible with standard ZIP tools. If a {@link ZipException}
 * is thrown during GZIP decompression (e.g. data is not compressed), the original input is returned as-is.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * byte[] input = ...;
 * byte[] compressed = CompressionUtils.compress(input, CompressionAlgorithm.GZIP);
 * byte[] restored = CompressionUtils.decompress(compressed, CompressionAlgorithm.GZIP);
 * }</pre>
 */
public class CompressionUtils {

    private static final LZ4Compressor lz4Compressor = LZ4Factory.fastestInstance().fastCompressor();
    private static final LZ4FastDecompressor lz4Decompressor = LZ4Factory.fastestInstance().fastDecompressor();

    /**
     * Compresses the given byte array using {@link CompressionAlgorithm#LZ4} by default.
     *
     * @param uncompressed the data to compress
     * @return the compressed byte array
     */
    public static byte[] compress(byte[] uncompressed) {
        return compress(uncompressed, CompressionAlgorithm.LZ4);
    }

    /**
     * Compresses the given byte array using the specified compression algorithm.
     *
     * @param uncompressed the data to compress
     * @param algorithm the compression algorithm to use
     * @return the compressed byte array
     */
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

    /**
     * Decompresses the given byte array using {@link CompressionAlgorithm#LZ4} by default.
     *
     * @param compressed the compressed data
     * @return the original (decompressed) byte array
     */
    public static byte[] decompress(byte[] compressed) {
        return decompress(compressed, CompressionAlgorithm.LZ4);
    }

    /**
     * Decompresses the given byte array using the specified algorithm.
     *
     * @param compressed the compressed data
     * @param algorithm the compression algorithm to apply
     * @return the original (decompressed) byte array
     */
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
