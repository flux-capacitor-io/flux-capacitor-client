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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class OutputStreamCapturer extends OutputStream {
    private final int chunkSize;
    private final ByteArrayOutputStream buffer;
    private final BiConsumer<byte[], Boolean> chunkHandler;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OutputStreamCapturer(int chunkSize, BiConsumer<byte[], Boolean> chunkHandler) {
        this.chunkSize = chunkSize;
        this.chunkHandler = chunkHandler;
        this.buffer = new ByteArrayOutputStream(chunkSize);
    }

    @Override
    public void write(int b) {
        buffer.write(b);
        flushIfNeeded();
    }

    @Override
    public void write(byte[] b, int off, int len) {
        buffer.write(b, off, len);
        flushIfNeeded();
    }

    private void flushIfNeeded() {
        if (buffer.size() >= chunkSize) {
            flush(false);
        }
    }

    public void flush(boolean isFinal) {
        if (buffer.size() > 0 || isFinal) {
            byte[] chunk = buffer.toByteArray();
            buffer.reset();
            chunkHandler.accept(chunk, isFinal);
        }
    }

    @Override
    public void flush() {
        if (closed.get()) {
            throw new IllegalStateException("Stream is closed");
        }
        flush(false);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            flush(true);
        }
    }
}