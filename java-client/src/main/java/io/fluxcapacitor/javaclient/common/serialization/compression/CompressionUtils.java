/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
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
