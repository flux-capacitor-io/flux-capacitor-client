/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

@Slf4j
public abstract class AbstractClientBenchmark {

    private final CountDownLatch        commandCountDownLatch;
    private final ApplicationProperties applicationProperties;

    public AbstractClientBenchmark(int commandCount, ApplicationProperties applicationProperties) {
        this.commandCountDownLatch = new CountDownLatch(commandCount);
        this.applicationProperties = applicationProperties;
    }

    public AbstractClientBenchmark(int commandCount) {
        this(commandCount, new ApplicationProperties("benchmark", "ws://localhost:8080"));
    }

    protected void testCommands() {
        int count = (int) commandCountDownLatch.getCount();
        log.info("Start sending {} commands", count);
        TimingUtils.time(() -> {
            IntStream.range(0, count).forEach(i -> doSendCommand("payload" + i));
            try {
                commandCountDownLatch.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }, duration -> log.info("Finished sending and handling {} commands in {}ms", count, duration));
    }

    protected abstract void doSendCommand(String payload);

    public CountDownLatch getCommandCountDownLatch() {
        return commandCountDownLatch;
    }

    public ApplicationProperties getApplicationProperties() {
        return applicationProperties;
    }
}
