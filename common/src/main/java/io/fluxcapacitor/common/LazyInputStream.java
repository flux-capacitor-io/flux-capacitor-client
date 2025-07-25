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

package io.fluxcapacitor.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

@SuppressWarnings("resource")
public class LazyInputStream extends InputStream {
    private final Supplier<? extends InputStream> supplier;
    private volatile InputStream delegate;

    public LazyInputStream(Supplier<? extends InputStream> supplier) {
        this.supplier = supplier;
    }

    private InputStream delegate() throws IOException {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = supplier.get();
                }
            }
        }
        return delegate;
    }

    @Override
    public int read() throws IOException {
        return delegate().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate().read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate().skip(n);
    }

    @Override
    public int available() throws IOException {
        return delegate().available();
    }

    @Override
    public void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        try {
            delegate().mark(readlimit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate().reset();
    }

    @Override
    public boolean markSupported() {
        try {
            return delegate().markSupported();
        } catch (IOException e) {
            return false;
        }
    }
}