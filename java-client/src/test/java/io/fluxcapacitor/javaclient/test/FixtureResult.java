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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents the result of a test phase within a {@link io.fluxcapacitor.javaclient.test.TestFixture}.
 * <p>
 * This object holds the complete state of outcomes produced during a {@code when} phase, including:
 * <ul>
 *     <li>The result or exception returned by the handler</li>
 *     <li>All published messages (commands, queries, events, web requests/responses, metrics)</li>
 *     <li>All scheduled messages</li>
 *     <li>All handler errors (excluding the final returned exception, if any)</li>
 *     <li>Any custom-topic messages</li>
 * </ul>
 * <p>
 * It also tracks whether result collection is currently active and optionally the {@link Message} that triggered the
 * action under test (the "traced" message).
 * <p>
 * This class is used internally by {@link io.fluxcapacitor.javaclient.test.TestFixture}.
 */
@Value
@NoArgsConstructor
public class FixtureResult {

    /**
     * Indicates whether the fixture is currently collecting results (e.g., during the {@code when} phase).
     */
    @NonFinal
    @Setter
    boolean collectingResults;

    /**
     * The message being traced as the input to the {@code when} phase, if any.
     * <p>
     * Used internally to avoid counting this message as a result when validating expectations.
     */
    @NonFinal
    @Setter
    Message tracedMessage;

    /**
     * The result or exception returned by the handler executed in the {@code when} phase.
     */
    @NonFinal
    @Setter
    Object result;

    /**
     * The result or exception returned by the previous {@code when} phase.
     */
    @NonFinal
    FixtureResult previousResult;

    public void setPreviousResult(FixtureResult previousResult) {
        this.previousResult = previousResult;
        if (previousResult != null) {
            this.knownWebParams.putAll(previousResult.knownWebParams);
        }
    }

    /**
     * A thread-safe mapping of web parameter keys to their corresponding values.
     * This map is used to store and manage web-related parameters that are known
     * and relevant to the operations or state of the system.
     */
    Map<String, String> knownWebParams = new ConcurrentHashMap<>();

    /**
     * All command messages published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> commands = new CopyOnWriteArrayList<>();

    /**
     * All query messages published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> queries = new CopyOnWriteArrayList<>();

    /**
     * All event messages published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> events = new CopyOnWriteArrayList<>();

    /**
     * All web requests published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> webRequests = new CopyOnWriteArrayList<>();

    /**
     * All web responses published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> webResponses = new CopyOnWriteArrayList<>();

    /**
     * All metric messages published during the {@code when} phase.
     */
    CopyOnWriteArrayList<Message> metrics = new CopyOnWriteArrayList<>();

    /**
     * All schedules that were newly created during the {@code when} phase.
     */
    CopyOnWriteArrayList<Schedule> schedules = new CopyOnWriteArrayList<>();

    /**
     * All errors thrown by message handlers during the {@code when} phase (excluding the final result exception).
     */
    CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

    /**
     * All messages published to custom topics, grouped by topic name.
     */
    Map<String, List<Message>> customMessages = new ConcurrentHashMap<>();
}
