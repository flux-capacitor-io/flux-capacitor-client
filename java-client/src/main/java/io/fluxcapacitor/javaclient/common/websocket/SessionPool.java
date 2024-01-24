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

package io.fluxcapacitor.javaclient.common.websocket;

import io.fluxcapacitor.common.ConsistentHashing;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;

@Slf4j
public class SessionPool implements AutoCloseable {
    private final List<AtomicReference<Session>> sessions;
    private final int size;
    private final AtomicInteger counter = new AtomicInteger();
    private final Supplier<Session> sessionFactory;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public SessionPool(int size, Supplier<Session> sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.sessions = IntStream.range(0, this.size = size).mapToObj(i -> new AtomicReference<Session>()).collect(
                toCollection(ArrayList::new));
    }

    public Session get() {
        return sessions.get(counter.getAndAccumulate(1, (i, inc) -> {
            int newIndex = i + inc;
            return newIndex >= size ? 0 : newIndex;
        })).updateAndGet(this::returnOrRefresh);
    }

    public Session get(String routingKey) {
        if (routingKey == null) {
            return get();
        }
        int segment = ConsistentHashing.computeSegment(routingKey, size);
        return sessions.get(segment).updateAndGet(this::returnOrRefresh);
    }

    protected Session returnOrRefresh(Session s) {
        if (isClosed(s)) {
            synchronized (shuttingDown) {
                while (isClosed(s)) {
                    if (shuttingDown.get()) {
                        throw new IllegalStateException("Cannot provide session. This client has closed");
                    }
                    s = sessionFactory.get();
                }
            }
        }
        return s;
    }

    @Override
    public void close() {
        if (shuttingDown.compareAndSet(false, true)) {
            synchronized (shuttingDown) {
                sessions.stream().map(AtomicReference::get).forEach(session -> {
                    if (!isClosed(session)) {
                        try {
                            session.close();
                        } catch (Exception e) {
                            log.warn("Failed to closed websocket session connected to endpoint {}. Reason: {}",
                                     session.getRequestURI(), e.getMessage());
                        }
                    }
                });
            }
        }
    }

    private static boolean isClosed(Session session) {
        try {
            return session == null || !session.isOpen();
        } catch (Exception e) {
            log.error("Failed to check if session is open", e);
            return true;
        }
    }
}
