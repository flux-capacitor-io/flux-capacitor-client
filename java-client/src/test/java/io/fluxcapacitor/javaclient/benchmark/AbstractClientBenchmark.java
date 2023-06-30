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

package io.fluxcapacitor.javaclient.benchmark;

import io.fluxcapacitor.common.TimingUtils;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static java.lang.Thread.currentThread;

@Slf4j
@Getter
public abstract class AbstractClientBenchmark {

    private final CountDownLatch commandCountDownLatch;
    private final WebSocketClient.ClientConfig clientConfig;
    private final int commandCount;

    public AbstractClientBenchmark(int commandCount, WebSocketClient.ClientConfig clientConfig) {
        this.commandCount = commandCount;
        this.commandCountDownLatch = new CountDownLatch(commandCount);
        this.clientConfig = clientConfig;
    }

    public AbstractClientBenchmark(int commandCount) {
        this(commandCount, WebSocketClient.ClientConfig.builder().name("benchmark-" + UUID.randomUUID())
                     .serviceBaseUrl("ws://localhost:8081").build());
    }

    protected void testCommands() {
        int count = (int) commandCountDownLatch.getCount();
        log.info("Start sending {} commands", count);
        TimingUtils.time(() -> {
            IntStream.range(0, count).forEach(i -> doSendCommand("payload" + i));
            try {
                commandCountDownLatch.await();
            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }, duration -> log.info("Finished sending and handling {} commands in {}ms", count, duration));
    }

    protected int getCommandCount() {
        return commandCount;
    }

    protected abstract void doSendCommand(String payload);
}
