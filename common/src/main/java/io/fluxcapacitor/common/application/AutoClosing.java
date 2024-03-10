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

package io.fluxcapacitor.common.application;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.ObjectUtils.newThreadName;
import static java.lang.Runtime.getRuntime;

public abstract class AutoClosing {

    private final AtomicBoolean stopped = new AtomicBoolean();

    protected AutoClosing() {
        getRuntime().addShutdownHook(new Thread(this::shutDown, newThreadName(getClass().getSimpleName() + "-shutdown")));
    }

    private void shutDown() {
        if (stopped.compareAndSet(false, true)) {
            onShutdown();
        }
    }

    protected boolean isStopped() {
        return stopped.get();
    }

    /**
     * Override to do something on shutdown
     */
    protected abstract void onShutdown();
}
