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

package io.fluxcapacitor.javaclient.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PartialFileInputStream extends InputStream {
    private final SeekableByteChannel channel;
    private long remaining;
    private final ByteBuffer buffer = ByteBuffer.allocate(8192);

    public PartialFileInputStream(Path file, long start, long length) throws IOException {
        this.channel = Files.newByteChannel(file, StandardOpenOption.READ);
        this.channel.position(start);
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int r = read(b, 0, 1);
        return (r == -1) ? -1 : b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int bytesToRead = (int) Math.min(len, remaining);
        buffer.clear().limit(Math.min(buffer.capacity(), bytesToRead));
        int read = channel.read(buffer);
        if (read == -1) {
            return -1;
        }
        buffer.flip();
        buffer.get(b, off, read);
        remaining -= read;
        return read;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}