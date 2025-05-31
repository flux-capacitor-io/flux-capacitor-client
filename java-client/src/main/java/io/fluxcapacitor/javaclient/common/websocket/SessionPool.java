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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A thread-safe pool of reusable WebSocket {@link Session} objects, supporting concurrent access and routing.
 * <p>
 * This class provides a mechanism to manage multiple active WebSocket sessions, either round-robin or keyed by a
 * {@code routingKey}. It lazily initializes sessions on demand using a configurable {@link Supplier}, ensuring that
 * each session slot is kept active unless the pool is shutting down or explicitly closed.
 * <p>
 * The session pool is particularly useful in high-throughput scenarios, where multiple sessions are used to distribute
 * load, improve parallelism, or isolate message streams.
 *
 * <h2>Usage Modes:</h2>
 * <ul>
 *     <li><strong>Round-robin</strong>: Calling {@link #get()} will return the next available session, cycling through
 *     the pool.</li>
 *     <li><strong>Hash-based routing</strong>: Calling {@link #get(String)} with a routing key returns the session
 *     consistently mapped to that key, based on consistent hashing.</li>
 * </ul>
 *
 * <p>
 * If a session is closed or unavailable, it is automatically replaced using the provided {@code sessionFactory}.
 * All sessions are closed when {@link #close()} is called.
 *
 * <p><strong>Note:</strong> This class is used by Flux WebSocket clients such as {@link AbstractWebsocketClient} to
 * manage their underlying connections to the Flux platform.
 *
 * @see AbstractWebsocketClient
 */
@Slf4j
public class SessionPool implements AutoCloseable {
    private final Map<Integer, Session> sessionMap;
    private final int size;
    private final AtomicInteger counter = new AtomicInteger();
    private final Supplier<Session> sessionFactory;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public SessionPool(int size, Supplier<Session> sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.size = size;
        this.sessionMap = new ConcurrentHashMap<>();
    }

    public Session get() {
        return get(counter.getAndAccumulate(1, (i, inc) -> {
            int newIndex = i + inc;
            return newIndex >= size ? 0 : newIndex;
        }));
    }

    public Session get(String routingKey) {
        if (routingKey == null) {
            return get();
        }
        return get(ConsistentHashing.computeSegment(routingKey, size));
    }

    protected Session get(int index) {
        return sessionMap.compute(index, (i, s) -> {
            while (isClosed(s)) {
                if (shuttingDown.get()) {
                    throw new IllegalStateException("Cannot provide session. This client has closed");
                }
                s = sessionFactory.get();
            }
            return s;
        });
    }

    @Override
    public void close() {
        if (shuttingDown.compareAndSet(false, true)) {
            synchronized (shuttingDown) {
                sessionMap.values().forEach(session -> {
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
